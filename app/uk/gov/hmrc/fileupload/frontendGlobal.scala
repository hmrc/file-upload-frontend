/*
 * Copyright 2016 HM Revenue & Customs
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

import akka.actor.ActorRef
import cats.data.Xor
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api.Mode._
import play.api.libs.json.JsObject
import play.api.mvc.{EssentialAction, Filters, Request}
import play.api.{Mode => _, _}
import play.filters.headers.SecurityHeadersFilter
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.fileupload.controllers.{AdminController, EnvelopeChecker, FileUploadController, UploadParser}
import uk.gov.hmrc.fileupload.infrastructure.{DefaultMongoConnection, HttpStreamingBody, PlayHttp}
import uk.gov.hmrc.fileupload.notifier.NotifierService.NotifyResult
import uk.gov.hmrc.fileupload.notifier.{NotifierRepository, NotifierService}
import uk.gov.hmrc.fileupload.quarantine.QuarantineService
import uk.gov.hmrc.fileupload.testonly.TestOnlyController
import uk.gov.hmrc.fileupload.transfer.TransferActor
import uk.gov.hmrc.fileupload.utils.{errorAsJson, ShowErrorAsJson}
import uk.gov.hmrc.fileupload.virusscan.ScanningService.{AvScanIteratee, ScanResult, ScanResultFileClean}
import uk.gov.hmrc.fileupload.virusscan.{ScannerActor, ScanningService, VirusScanner}
import uk.gov.hmrc.play.audit.filters.FrontendAuditFilter
import uk.gov.hmrc.play.audit.http.config.ErrorAuditingSettings
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.frontend.bootstrap.Routing.RemovingOfTrailingSlashes
import uk.gov.hmrc.play.frontend.bootstrap.{FrontendFilters, Routing}
import uk.gov.hmrc.play.frontend.filters.{DeviceIdCookieFilter, SecurityHeadersFilterFactory}
import uk.gov.hmrc.play.graphite.GraphiteConfig
import uk.gov.hmrc.play.http.logging.filters.FrontendLoggingFilter

import scala.concurrent.Future

object FrontendGlobal extends GlobalSettings with FrontendFilters with GraphiteConfig
  with RemovingOfTrailingSlashes with Routing.BlockingOfPaths with ErrorAuditingSettings with ShowErrorAsJson {

  lazy val appName = Play.current.configuration.getString("appName").getOrElse("APP NAME NOT SET")
  lazy val enableSecurityHeaderFilter = Play.current.configuration.getBoolean("security.headers.filter.enabled").getOrElse(true)

  override lazy val deviceIdFilter = DeviceIdCookieFilter(appName, auditConnector)

  def filters = if (enableSecurityHeaderFilter) Seq(securityFilter) ++ frontendFilters  else frontendFilters

  override def doFilter(a: EssentialAction): EssentialAction =
    Filters(super.doFilter(a), filters: _* )

  override def securityFilter: SecurityHeadersFilter = SecurityHeadersFilterFactory.newInstance

  override val auditConnector = FrontendAuditConnector
  override val loggingFilter = LoggingFilter
  override val frontendAuditFilter = AuditFilter

  import play.api.libs.concurrent.Execution.Implicits._

  var subscribe: (ActorRef, Class[_]) => Boolean = _
  var publish: (AnyRef) => Unit = _
  var notifyAndPublish: (AnyRef) => Future[NotifyResult] = _
  val now: () => Long = () => System.currentTimeMillis()

  override def onStart(app: Application) {
    Logger.info(s"Starting frontend : $appName : in mode : ${app.mode}")
    super.onStart(app)
    ApplicationCrypto.verifyConfiguration()

    // event stream
    import play.api.Play.current
    import play.api.libs.concurrent.Akka
    val eventStream = Akka.system.eventStream
    subscribe = eventStream.subscribe
    publish = eventStream.publish

    notifyAndPublish = NotifierService.notify(sendNotification, publish) _

    // scanner
    Akka.system.actorOf(ScannerActor.props(subscribe, scanBinaryData, notifyAndPublish), "scannerActor")
    Akka.system.actorOf(TransferActor.props(subscribe, streamTransferCall), "transferActor")

    fileUploadController
    adminController
    testOnlyController
  }


  override def onStop(app: Application): Unit = {
    super.onStop(app)
  }

  override def onLoadConfig(config: Configuration, path: java.io.File, classloader: ClassLoader, mode: Mode): Configuration = {
    super.onLoadConfig(config, path, classloader, mode)
  }

  override def standardErrorTemplate(message: String)(implicit rh: Request[_]): JsObject = {
    errorAsJson(message)
  }

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")

  // db

  lazy val db = DefaultMongoConnection.db

  // quarantine
  lazy val quarantineRepository = quarantine.Repository(db)
  lazy val retrieveFile = quarantineRepository.retrieveFile _
  lazy val getFileFromQuarantine= QuarantineService.getFileFromQuarantine(retrieveFile) _
  lazy val recreateCollections = () => quarantineRepository.recreate()

  // auditing
  lazy val auditedHttpExecute = PlayHttp.execute(auditConnector, ServiceConfig.appName, Some(t => Logger.warn(t.getMessage, t))) _
  lazy val auditF: (Boolean, Int, String) => (Request[_]) => Future[AuditResult] =
    PlayHttp.audit(auditConnector, ServiceConfig.appName, Some(t => Logger.warn(t.getMessage, t)))
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
  lazy val isEnvelopeAvailable = transfer.Repository.envelopeAvailable(auditedHttpExecute, ServiceConfig.fileUploadBackendBaseUrl) _

  lazy val status = transfer.Repository.envelopeStatus(auditedHttpExecute,ServiceConfig.fileUploadBackendBaseUrl) _

  lazy val envelopeAvailable = transfer.TransferService.envelopeAvailable(isEnvelopeAvailable) _

  lazy val envelopeStatus = transfer.TransferService.envelopeStatus(status) _

  lazy val streamTransferCall = transfer.TransferService.stream(
    ServiceConfig.fileUploadBackendBaseUrl, publish, auditedHttpBodyStreamer, getFileFromQuarantine) _

  // upload
  lazy val uploadParser = () => UploadParser.parse(quarantineRepository.writeFile) _

  lazy val scanner: () => AvScanIteratee = VirusScanner.scanIteratee
  lazy val scanBinaryData: (FileRefId) => Future[ScanResult] = {
    import play.api.Play.current

    val runStubClam = ServiceConfig.clamAvConfig.flatMap(_.getBoolean("runStub")).getOrElse(false)
    if (runStubClam & Play.isDev) {
      (_: FileRefId) => Future.successful(Xor.right(ScanResultFileClean))
    } else {
      ScanningService.scanBinaryData(scanner, getFileFromQuarantine)
    }
  }

  // notifier
  //TODO: inject proper toConsumerUrl function
  lazy val sendNotification = NotifierRepository.send(auditedHttpExecute, ServiceConfig.fileUploadBackendBaseUrl) _

  lazy val withValidEnvelope = EnvelopeChecker.withValidEnvelope(envelopeStatus) _

  lazy val fileUploadController = new FileUploadController(withValidEnvelope,  uploadParser = uploadParser, notify = notifyAndPublish, now = now)

  lazy val adminController = new AdminController(notify = notifyAndPublish)

  private val FileUploadControllerClass = classOf[FileUploadController]
  private val AdminControllerClass = classOf[AdminController]

  lazy val testOnlyController = new TestOnlyController(ServiceConfig.fileUploadBackendBaseUrl, recreateCollections)
  private val TestOnlyControllerClass = classOf[TestOnlyController]

  override def getControllerInstance[A](controllerClass: Class[A]): A = {
    controllerClass match {
      case FileUploadControllerClass => fileUploadController.asInstanceOf[A]
      case AdminControllerClass => adminController.asInstanceOf[A]
      case TestOnlyControllerClass => testOnlyController.asInstanceOf[A]
      case _ => super.getControllerInstance(controllerClass)
    }
  }
}

object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object LoggingFilter extends FrontendLoggingFilter {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object AuditFilter extends FrontendAuditFilter with RunMode with AppName {

  override lazy val maskedFormFields = Seq("password")

  override lazy val applicationPort = None

  override lazy val auditConnector = FrontendAuditConnector

  override def controllerNeedsAuditing(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsAuditing
}
