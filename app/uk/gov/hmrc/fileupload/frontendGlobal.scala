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

import cats.data.Xor
import akka.actor.ActorRef
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import org.joda.time.Duration
import play.api.mvc.Request
import play.api.{Mode => _, _}
import play.api.mvc.{BodyParser, Request}
import play.api.{Application, Configuration, Logger, Play}
import play.twirl.api.Html
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.fileupload.controllers.{AdminController, EnvelopeChecker, FileUploadController, UploadParser}
import uk.gov.hmrc.fileupload.infrastructure.{DefaultMongoConnection, HttpStreamingBody, PlayHttp}
import uk.gov.hmrc.fileupload.notifier.NotifierService.NotifyResult
import uk.gov.hmrc.fileupload.notifier.{NotifierRepository, NotifierService}
import uk.gov.hmrc.fileupload.quarantine.{FileInfo, QuarantineService}
import uk.gov.hmrc.fileupload.testonly.TestOnlyController
import uk.gov.hmrc.fileupload.transfer.TransferActor
import uk.gov.hmrc.fileupload.virusscan.ScanningService.{AvScanIteratee, ScanResult, ScanResultFileClean}
import uk.gov.hmrc.fileupload.virusscan.{ScannerActor, ScanningService, VirusScanner}
import uk.gov.hmrc.play.audit.filters.FrontendAuditFilter
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.http.logging.filters.FrontendLoggingFilter
import uk.gov.hmrc.play.filters.MicroserviceFilterSupport
import play.api.i18n.Messages.Implicits._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.streams.Streams
import play.mvc.BodyParser.MultipartFormData
import uk.gov.hmrc.fileupload.fileupload.JSONReadFile

import scala.concurrent.Future


object Implicits {
  implicit val system = Akka.system
  implicit val materializer = ActorMaterializer()
}
import Implicits._

object FileUploadController extends FileUploadController(uploadParser = FrontendGlobal.uploadParser,
  notify = FrontendGlobal.notifyAndPublish, now = FrontendGlobal.now, clearFiles = FrontendGlobal.clearFiles)

object TestOnlyController extends TestOnlyController(ServiceConfig.fileUploadBackendBaseUrl, FrontendGlobal.removeAllFiles)


object FrontendGlobal extends DefaultFrontendGlobal {

  override val auditConnector = FrontendAuditConnector
  override val loggingFilter = LoggingFilter
  override val frontendAuditFilter = AuditFilter


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

  }

  override def standardErrorTemplate(pageTitle: String, heading: String, message: String)(implicit rh: Request[_]): Html =
    uk.gov.hmrc.fileupload.views.html.error_template(pageTitle, heading, message)(rh, applicationMessages)

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")

  // db

  lazy val db = DefaultMongoConnection.db

  // quarantine
  lazy val quarantineRepository = quarantine.Repository(db)
  lazy val retrieveFile = quarantineRepository.retrieveFile _
  lazy val getFileFromQuarantine= QuarantineService.getFileFromQuarantine(retrieveFile) _
  lazy val recreateCollections = () => quarantineRepository.recreate()
  lazy val getFileInfo = quarantineRepository.retrieveFileMetaData _
  lazy val getFileChunksInfo = quarantineRepository.chunksCount _

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
    if (runStubClam & (Play.isDev ||Play.isTest)) {
      (_: FileRefId) => Future.successful(Xor.right(ScanResultFileClean))
    } else {
      ScanningService.scanBinaryData(scanner, getFileFromQuarantine)
    }
  }

  // notifier
  //TODO: inject proper toConsumerUrl function
  lazy val sendNotification = NotifierRepository.send(auditedHttpExecute, ServiceConfig.fileUploadBackendBaseUrl) _

  lazy val withValidEnvelope = EnvelopeChecker.withValidEnvelope(envelopeStatus) _

}

object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object LoggingFilter extends FrontendLoggingFilter with MicroserviceFilterSupport{
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object AuditFilter extends FrontendAuditFilter with RunMode with AppName with MicroserviceFilterSupport{

  override lazy val maskedFormFields = Seq("password")

  override lazy val applicationPort = None

  override lazy val auditConnector = FrontendAuditConnector

  override def controllerNeedsAuditing(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsAuditing
}
