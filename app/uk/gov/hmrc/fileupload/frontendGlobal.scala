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
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api.Mode._
import play.api.mvc.Request
import play.api.{Application, Configuration, Logger, Play}
import play.twirl.api.Html
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.fileupload.controllers.{FileUploadController, UploadParser}
import uk.gov.hmrc.fileupload.infrastructure.{DefaultMongoConnection, PlayHttp}
import uk.gov.hmrc.fileupload.notifier.{NotifierActor, NotifierRepository}
import uk.gov.hmrc.fileupload.testonly.TestOnlyController
import uk.gov.hmrc.fileupload.virusscan.ScanningService.AvScanIteratee
import uk.gov.hmrc.fileupload.virusscan.{ScanningService, VirusScanner}
import uk.gov.hmrc.play.audit.filters.FrontendAuditFilter
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.frontend.bootstrap.DefaultFrontendGlobal
import uk.gov.hmrc.play.http.logging.filters.FrontendLoggingFilter

object FrontendGlobal
  extends DefaultFrontendGlobal {

  override val auditConnector = FrontendAuditConnector
  override val loggingFilter = LoggingFilter
  override val frontendAuditFilter = AuditFilter

  import play.api.libs.concurrent.Execution.Implicits._

  var subscribe: (ActorRef, Class[_]) => Boolean = _
  var publish: (AnyRef) => Unit = _

  override def onStart(app: Application) {
    super.onStart(app)
    ApplicationCrypto.verifyConfiguration()

    // event stream
    import play.api.Play.current
    import play.api.libs.concurrent.Akka
    val eventStream = Akka.system.eventStream
    subscribe = eventStream.subscribe
    publish = eventStream.publish

    // notifier
    Akka.system.actorOf(NotifierActor.props(subscribe, envelopeCallback, sendNotification), "notifierActor")

    fileUploadController
    testOnlyController
  }

  override def onLoadConfig(config: Configuration, path: java.io.File, classloader: ClassLoader, mode: Mode): Configuration = {
    super.onLoadConfig(config, path, classloader, mode)
  }

  override def standardErrorTemplate(pageTitle: String, heading: String, message: String)(implicit rh: Request[_]): Html =
    uk.gov.hmrc.fileupload.views.html.error_template(pageTitle, heading, message)

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")

  // db

  lazy val db = DefaultMongoConnection.db

  // quarantine
  lazy val quarantineRepository = quarantine.Repository(db)
  lazy val retrieveFile = quarantineRepository.retrieveFile _

  // auditing
  lazy val auditedHttpExecute = PlayHttp.execute(auditConnector, ServiceConfig.appName, Some(t => Logger.warn(t.getMessage, t))) _

  // transfer
  lazy val findEnvelopeCallback = transfer.Repository.envelopeCallback(auditedHttpExecute, ServiceConfig.fileUploadBackendBaseUrl) _
  lazy val isEnvelopeAvailable = transfer.Repository.envelopeAvailable(auditedHttpExecute, ServiceConfig.fileUploadBackendBaseUrl) _

  lazy val envelopeAvailable = transfer.TransferService.envelopeAvailable(isEnvelopeAvailable) _
  lazy val envelopeCallback = transfer.TransferService.envelopeCallback(findEnvelopeCallback) _
  lazy val streamTransferCall = transfer.TransferService.stream(ServiceConfig.fileUploadBackendBaseUrl, publish) _

  // upload
  lazy val uploadParser = () => UploadParser.parse(quarantineRepository.writeFile) _
  lazy val uploadFile = upload.UploadService.upload(envelopeAvailable, streamTransferCall, null, null) _

  lazy val scanner: () => AvScanIteratee = VirusScanner.scanIteratee
  lazy val scanBinaryData = ScanningService.scanBinaryData(scanner)(publish) _

  // notifier
  //TODO: inject proper toConsumerUrl function
  lazy val sendNotification = NotifierRepository.notify(auditedHttpExecute) _

  lazy val fileUploadController = new FileUploadController(uploadParser = uploadParser,
    transferToTransient = uploadFile,
    retrieveFile = retrieveFile,
    scanBinaryData = scanBinaryData,
    publish = publish)

  private val FileUploadControllerClass = classOf[FileUploadController]

  lazy val testOnlyController = new TestOnlyController(ServiceConfig.fileUploadBackendBaseUrl)
  private val TestOnlyControllerClass = classOf[TestOnlyController]

  override def getControllerInstance[A](controllerClass: Class[A]): A = {
    controllerClass match {
      case FileUploadControllerClass => fileUploadController.asInstanceOf[A]
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
