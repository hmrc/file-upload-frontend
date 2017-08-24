/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.fileupload

import java.net.InetSocketAddress
import java.util.concurrent.Executors
import javax.inject.Provider

import akka.actor.ActorRef
import cats.data.Xor
import com.codahale.metrics.graphite.{Graphite, GraphiteReporter}
import com.codahale.metrics.{MetricFilter, SharedMetricRegistries}
import com.kenshoo.play.metrics.{MetricsController, MetricsImpl}
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.Logger
import play.api.ApplicationLoader.Context
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WSRequest
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.{EssentialFilter, Request}
import play.api.{BuiltInComponentsFromContext, LoggerConfigurator, Mode}
import play.modules.reactivemongo.ReactiveMongoComponentImpl
import uk.gov.hmrc.fileupload.controllers._
import uk.gov.hmrc.fileupload.filters.{UserAgent, UserAgentRequestFilter}
import uk.gov.hmrc.fileupload.infrastructure.{HttpStreamingBody, PlayHttp}
import uk.gov.hmrc.fileupload.notifier.CommandHandlerImpl
import uk.gov.hmrc.fileupload.quarantine.QuarantineService
import uk.gov.hmrc.fileupload.s3.{InMemoryMultipartFileHandler, S3JavaSdkService, S3Key, S3KeyName}
import uk.gov.hmrc.fileupload.testonly.TestOnlyController
import uk.gov.hmrc.fileupload.transfer.TransferActor
import uk.gov.hmrc.fileupload.utils.ShowErrorAsJson
import uk.gov.hmrc.fileupload.virusscan.ScanningService.{AvScanIteratee, ScanResult, ScanResultFileClean}
import uk.gov.hmrc.fileupload.virusscan.{ScannerActor, ScanningService, VirusScanner}
import uk.gov.hmrc.fileupload.zip.MongoS3Compability
import uk.gov.hmrc.fileupload.zip.MongoS3Compability.GetFileResult
import uk.gov.hmrc.play.audit.filters.AuditFilter
import uk.gov.hmrc.play.audit.http.config.LoadAuditingConfig
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode, ServicesConfig}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.filters.LoggingFilter

import scala.concurrent.{ExecutionContext, Future}


class ApplicationLoader extends play.api.ApplicationLoader {
  def load(context: Context) = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment)
    }
    val appModule = new ApplicationModule(context)
    appModule.graphiteStart()
    appModule.application
  }
}

class ApplicationModule(context: Context) extends BuiltInComponentsFromContext(context)
  with AhcWSComponents with AppName with ServicesConfig {

  override lazy val appName = configuration.getString("appName").getOrElse("APP NAME NOT SET")
  override lazy val httpErrorHandler = new ShowErrorAsJson(environment, configuration)

  override lazy val mode = context.environment.mode
  override lazy val runModeConfiguration = configuration

  lazy val healthRoutes = new manualdihealth.Routes(httpErrorHandler, new uk.gov.hmrc.play.health.AdminController(configuration))
  lazy val appRoutes = new app.Routes(
    httpErrorHandler,
    fileUploadController,
    fileDownloadController,
    fileInQuarantineController
  )

  lazy val adminRoutes = new admin.Routes(httpErrorHandler, adminController)

  lazy val metrics = new MetricsImpl(applicationLifecycle, configuration)
  lazy val metricsController = new MetricsController(metrics)

  lazy val prodRoutes = new prod.Routes(httpErrorHandler, new Provider[MetricsController] {
    override def get(): MetricsController = metricsController
  }, healthRoutes, appRoutes, adminRoutes)

  lazy val router = if (configuration.getString("application.router").get == "testOnlyDoNotUseInAppConf.Routes") testRoutes else prodRoutes

  lazy val inMemoryBodyParser = InMemoryMultipartFileHandler.parser

  lazy val s3Service = new S3JavaSdkService(configuration.underlying, metrics.defaultRegistry)

  lazy val downloadFromTransient = s3Service.downloadFromTransient

  lazy val uploadToQuarantine = s3Service.uploadToQuarantine

  lazy val createS3Key = S3Key.forEnvSubdir(s3Service.awsConfig.envSubdir)

  val redirectionFeature = new RedirectionFeature(configuration.underlying)

  lazy val fileDownloadController =
    new FileDownloadController(
      downloadFromTransient,
      (e, f) => S3KeyName(createS3Key(e, f)),
      now,
      s3Service.downloadFromQuarantine
    )

  lazy val fileUploadController =
    new FileUploadController(redirectionFeature, withValidEnvelope, inMemoryBodyParser, commandHandler, uploadToQuarantine, createS3Key, now)

  // TMP walkaround for legacy files in mongo quaratine
  lazy val mongoQuaratineGetFile: (FileRefId) => Future[GetFileResult] =
    MongoS3Compability.retrieveFileFromMongoDB(
      quarantineRepository.retrieveFile _
    ) _

  lazy val fileInQuarantineController =
    new FileController(mongoQuaratineGetFile)

  // TMP END

  lazy val fileUploadBackendBaseUrl = baseUrl("file-upload-backend")

  lazy val healthController = new uk.gov.hmrc.play.health.AdminController(configuration)

  lazy val testOnlyController = new TestOnlyController(fileUploadBackendBaseUrl, recreateCollections, wsClient, s3Service)

  lazy val testRoutes = new testOnlyDoNotUseInAppConf.Routes(httpErrorHandler, testOnlyController, prodRoutes)

  lazy val adminController = new AdminController(getFileInfo = getFileInfo, getChunks = getFileChunksInfo)(commandHandler)

  var subscribe: (ActorRef, Class[_]) => Boolean = _
  var publish: (AnyRef) => Unit = _
  val now: () => Long = () => System.currentTimeMillis()

  subscribe = actorSystem.eventStream.subscribe
  publish = actorSystem.eventStream.publish

  lazy val commandHandler = new CommandHandlerImpl(auditedHttpExecute, fileUploadBackendBaseUrl, wsClient, publish)

  lazy val getFileLength = {
    (envelopeId: EnvelopeId, fileId: FileId, version: FileRefId) =>
      s3Service.getFileLengthFromQuarantine(createS3Key(envelopeId, fileId), version.value)
  }
  // scanner
  actorSystem.actorOf(ScannerActor.props(subscribe, scanBinaryData, commandHandler), "scannerActor")
  actorSystem.actorOf(TransferActor.props(subscribe, createS3Key, commandHandler, getFileLength, s3Service.copyFromQtoT), "transferActor")

  // db
  lazy val db = new ReactiveMongoComponentImpl(application, applicationLifecycle).mongoConnector.db

  // quarantine
  lazy val quarantineRepository = quarantine.Repository(db)
  //lazy val retrieveFileFromQuarantineBucket = s3Service.retrieveFileFromQuarantine _
  //TODO discuss alternative thread pools
  lazy val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(25))
  lazy val getFileFromQuarantine = QuarantineService.getFileFromQuarantine(s3Service.retrieveFileFromQuarantine)(_: String, _: String)(ec)
  lazy val recreateCollections = () => quarantineRepository.recreate()
  lazy val getFileInfo = quarantineRepository.retrieveFileMetaData _
  lazy val getFileChunksInfo = quarantineRepository.chunksCount _

  // auditing
  lazy val auditedHttpExecute = PlayHttp.execute(AuditFilter.auditConnector, appName, Some(t => Logger.warn(t.getMessage, t))) _
  lazy val auditF: (Boolean, Int, String) => (Request[_]) => Future[AuditResult] =
    PlayHttp.audit(AuditFilter.auditConnector, appName, Some(t => Logger.warn(t.getMessage, t)))
  val auditedHttpBodyStreamer = (baseUrl: String, envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId, request: Request[_]) =>
    new HttpStreamingBody(
      url = s"$baseUrl/file-upload/envelopes/${envelopeId.value}/files/${fileId.value}/${fileRefId.value}",
      contentType = "application/octet-stream",
      method = "PUT",
      auditer = auditF,
      request,
      contentLength = None,
      debug = true
    )

  // transfer
  lazy val isEnvelopeAvailable = transfer.Repository.envelopeAvailable(auditedHttpExecute, fileUploadBackendBaseUrl, wsClient) _

  lazy val envelopeJsonResult = transfer.Repository.envelopeDetail(auditedHttpExecute, fileUploadBackendBaseUrl, wsClient) _

  lazy val envelopeResult = transfer.TransferService.envelopeResult(envelopeJsonResult) _

  lazy val streamTransferCall = transfer.TransferService.stream(
    fileUploadBackendBaseUrl, publish, auditedHttpBodyStreamer, getFileFromQuarantine)(createS3Key) _

  // upload
  lazy val uploadParser = () => UploadParser.parse(quarantineRepository.writeFile) _

  lazy val scanner: () => AvScanIteratee = new VirusScanner(configuration, environment).scanIteratee
  lazy val scanBinaryData: (EnvelopeId, FileId, FileRefId) => Future[ScanResult] = {

    val runStubClam = configuration.getConfig(s"${environment.mode}.clam.antivirus").flatMap(_.getBoolean("runStub")).getOrElse(false)
    if (runStubClam & (environment.mode == Mode.Dev || environment.mode == Mode.Test)) {
      (_: EnvelopeId, _: FileId, _: FileRefId) => Future.successful(Xor.right(ScanResultFileClean))
    } else {
      ScanningService.scanBinaryData(scanner, getFileFromQuarantine)(createS3Key)
    }
  }


  override lazy val httpFilters: Seq[EssentialFilter] =
    Seq(new UserAgentRequestFilter(metrics.defaultRegistry, UserAgent.allKnown, UserAgent.defaultIgnoreList))

  lazy val withValidEnvelope = EnvelopeChecker.withValidEnvelope(envelopeResult) _

  object ControllerConfiguration extends ControllerConfig {
    lazy val controllerConfigs = configuration.underlying.as[Config]("controllers")
  }

  object LoggingFilter extends LoggingFilter {
    override def mat = materializer

    override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
  }

  object MicroserviceAuditConnector extends AuditConnector with RunMode {
    override lazy val auditingConfig = LoadAuditingConfig(s"auditing")

    override def buildRequest(url: String)(implicit hc: HeaderCarrier): WSRequest = {
      wsApi.url(url).withHeaders(hc.headers: _*)
    }
  }

  object AuditFilter extends AuditFilter with AppName {
    override def mat = materializer

    override lazy val auditConnector = MicroserviceAuditConnector

    override def controllerNeedsAuditing(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsAuditing

    override lazy val appName = configuration.getString("appName").getOrElse("APP NAME NOT SET")
  }


  def graphiteStart(): Unit = {
    val graphiteConfig = configuration.getConfig(s"$env.microservice.metrics")

    def graphitePublisherEnabled: Boolean = {
      val status = graphiteConfig.flatMap(_.getBoolean("graphite.enabled")).getOrElse(false)
      Logger.info(s"graphitePublisherEnabled: $env=$status")
      status
    }

    if (graphitePublisherEnabled) {
      val metricsConfig = graphiteConfig.getOrElse(throw new Exception("The application does not contain required metrics configuration"))

      val graphite = new Graphite(new InetSocketAddress(
        metricsConfig.getString("graphite.host").getOrElse("graphite"),
        metricsConfig.getInt("graphite.port").getOrElse(2003)))

      val prefix = metricsConfig.getString("graphite.prefix").getOrElse(s"tax.${configuration.getString("appName")}")

      import java.util.concurrent.TimeUnit._

      val reporter = GraphiteReporter.forRegistry(
        SharedMetricRegistries.getOrCreate(graphiteConfig.flatMap(_.getString("metrics.name")).getOrElse("default")))
        .prefixedWith(s"$prefix.${java.net.InetAddress.getLocalHost.getHostName}")
        .convertRatesTo(SECONDS)
        .convertDurationsTo(MILLISECONDS)
        .filter(MetricFilter.ALL)
        .build(graphite)

      reporter.start(metricsConfig.getLong("graphite.interval").getOrElse(10L), SECONDS)
    }
  }

}
