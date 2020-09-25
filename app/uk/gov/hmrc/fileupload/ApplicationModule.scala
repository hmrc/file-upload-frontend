/*
 * Copyright 2020 HM Revenue & Customs
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

import akka.actor.ActorRef
import cats.data.Xor
import com.codahale.metrics.graphite.{Graphite, GraphiteReporter}
import com.codahale.metrics.{MetricFilter, SharedMetricRegistries}
import com.kenshoo.play.metrics.{MetricsController, MetricsImpl}
import com.typesafe.config.Config
import javax.inject.{Inject, Provider, Singleton}
import net.ceedubs.ficus.Ficus._
import play.Logger
import play.api.ApplicationLoader.Context
import play.api.Mode.Mode
import play.api._
import play.api.libs.ws.ahc.AhcWSComponents
import play.api.mvc.{EssentialFilter, Request}
import uk.gov.hmrc.fileupload.controllers._
import uk.gov.hmrc.fileupload.filters.{UserAgent, UserAgentRequestFilter}
import uk.gov.hmrc.fileupload.infrastructure.{HttpStreamingBody, PlayHttp}
import uk.gov.hmrc.fileupload.notifier.{CommandHandler, CommandHandlerImpl}
import uk.gov.hmrc.fileupload.quarantine.QuarantineService
import uk.gov.hmrc.fileupload.s3.S3Service.DeleteFileFromQuarantineBucket
import uk.gov.hmrc.fileupload.s3._
import uk.gov.hmrc.fileupload.testonly.TestOnlyController
import uk.gov.hmrc.fileupload.transfer.TransferActor
import uk.gov.hmrc.fileupload.utils.{LoggerHelper, LoggerHelperFileExtensionAndUserAgent, ShowErrorAsJson}
import uk.gov.hmrc.fileupload.virusscan.{AvClient, DeletionActor, ScannerActor, ScanningService, VirusScanner}
import uk.gov.hmrc.fileupload.virusscan.ScanningService.{AvScan, ScanResult, ScanResultFileClean}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
// import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode, ServicesConfig}
// import uk.gov.hmrc.play.frontend.config.LoadAuditingConfig
// import uk.gov.hmrc.play.frontend.filters.{FrontendAuditFilter, LoggingFilter}

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class ApplicationModule @Inject()(
  servicesConfig          : ServicesConfig,
  auditConnector          : AuditConnector,
  metrics                 : MetricsImpl,
  avClient                : AvClient,
  actorSystem             : akka.actor.ActorSystem,
  val applicationLifecycle: play.api.inject.ApplicationLifecycle,
  val configuration       : play.api.Configuration,
  val environment         : play.api.Environment
)(implicit
  val executionContext: scala.concurrent.ExecutionContext,
  val materializer    : akka.stream.Materializer
) extends AhcWSComponents {

  lazy val httpErrorHandler = new ShowErrorAsJson(environment, configuration)

  lazy val inMemoryBodyParser = InMemoryMultipartFileHandler.parser

  lazy val s3Service = new S3JavaSdkService(configuration.underlying, metrics.defaultRegistry)

  lazy val downloadFromTransient = s3Service.downloadFromTransient

  lazy val uploadToQuarantine = s3Service.uploadToQuarantine

  lazy val deleteObjectFromQuarantineBucket: DeleteFileFromQuarantineBucket = s3Service.deleteObjectFromQuarantine

  lazy val createS3Key = S3Key.forEnvSubdir(s3Service.awsConfig.envSubdir)

  val redirectionFeature = new RedirectionFeature(configuration.underlying, httpErrorHandler)

  lazy val zipAndPresign = s3Service.zipAndPresign _

  val downloadFromQuarantine = s3Service.downloadFromQuarantine

  lazy val loggerHelper: LoggerHelper = new LoggerHelperFileExtensionAndUserAgent

  lazy val fileUploadBackendBaseUrl = servicesConfig.baseUrl("file-upload-backend")

  lazy val testOnlySdesStubIsEnabled = configuration.getOptional[Boolean]("sdes-stub.enabled").getOrElse(false)
  lazy val optTestOnlySdesStubBaseUrl = if (testOnlySdesStubIsEnabled) Some(servicesConfig.baseUrl("sdes-stub")) else None


  var subscribe: (ActorRef, Class[_]) => Boolean = _
  var publish: (AnyRef) => Unit = _
  val now: () => Long = () => System.currentTimeMillis()

  subscribe = actorSystem.eventStream.subscribe
  publish = actorSystem.eventStream.publish

  lazy val commandHandler: CommandHandler = new CommandHandlerImpl(auditedHttpExecute, fileUploadBackendBaseUrl, wsClient, publish)

  lazy val getFileLength = {
    (envelopeId: EnvelopeId, fileId: FileId, version: FileRefId) =>
      s3Service.getFileLengthFromQuarantine(createS3Key(envelopeId, fileId), version.value)
  }

  // scanner
  actorSystem.actorOf(ScannerActor.props(subscribe, scanBinaryData, commandHandler), "scannerActor")
  actorSystem.actorOf(TransferActor.props(subscribe, createS3Key, commandHandler, getFileLength, s3Service.copyFromQtoT), "transferActor")
  actorSystem.actorOf(DeletionActor.props(subscribe, deleteObjectFromQuarantineBucket, createS3Key), "deletionActor")


  //lazy val retrieveFileFromQuarantineBucket = s3Service.retrieveFileFromQuarantine _
  //TODO discuss alternative thread pools
  lazy val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(25))
  lazy val getFileFromQuarantine = QuarantineService.getFileFromQuarantine(s3Service.retrieveFileFromQuarantine)(_: S3KeyName, _: String)(ec)

  // auditing
  lazy val auditedHttpExecute = PlayHttp.execute(auditConnector, "file-upload-frontend", Some(t => Logger.warn(t.getMessage, t))) _
  lazy val auditF: (Boolean, Int, String) => (Request[_]) => Future[AuditResult] =
    PlayHttp.audit(auditConnector, "file-upload-frontend", Some(t => Logger.warn(t.getMessage, t)))
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


  lazy val numberOfTimeoutAttempts: Int = configuration.getInt(s"${environment.mode}.clam.antivirus.numberOfTimeoutAttempts").getOrElse(1)
  lazy val scanner: AvScan = new VirusScanner(avClient).scan
  lazy val scanBinaryData: (EnvelopeId, FileId, FileRefId) => Future[ScanResult] = {
    val disableScanning = configuration.getConfig(s"${environment.mode}.clam.antivirus")
                            .flatMap(_.getBoolean("disableScanning")).getOrElse(false)
    if (disableScanning) (_: EnvelopeId, _: FileId, _: FileRefId) => Future.successful(Xor.right(ScanResultFileClean))
    else ScanningService.scanBinaryData(scanner, numberOfTimeoutAttempts, getFileFromQuarantine)(createS3Key)
  }

  lazy val withValidEnvelope = EnvelopeChecker.withValidEnvelope(envelopeResult) _
}
