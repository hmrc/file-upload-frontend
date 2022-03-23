/*
 * Copyright 2022 HM Revenue & Customs
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

import java.util.concurrent.Executors

import akka.actor.ActorRef
import com.kenshoo.play.metrics.MetricsImpl
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.http.HttpErrorHandler
import play.api.libs.ws.ahc.AhcWSComponents
import uk.gov.hmrc.fileupload.controllers.{EnvelopeChecker, RedirectionFeature}
import uk.gov.hmrc.fileupload.infrastructure.PlayHttp
import uk.gov.hmrc.fileupload.notifier.{CommandHandler, CommandHandlerImpl}
import uk.gov.hmrc.fileupload.quarantine.QuarantineService
import uk.gov.hmrc.fileupload.s3.S3Service.DeleteFileFromQuarantineBucket
import uk.gov.hmrc.fileupload.s3.{S3JavaSdkService, S3Key, S3KeyName}
import uk.gov.hmrc.fileupload.transfer.TransferActor
import uk.gov.hmrc.fileupload.utils.{LoggerHelper, LoggerHelperFileExtensionAndUserAgent}
import uk.gov.hmrc.fileupload.virusscan.{AvClient, DeletionActor, ScannerActor, ScanningService, VirusScanner}
import uk.gov.hmrc.fileupload.virusscan.ScanningService.{AvScan, ScanResult, ScanResultFileClean}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class ApplicationModule @Inject()(
  servicesConfig          : ServicesConfig,
  auditConnector          : AuditConnector,
  metrics                 : MetricsImpl,
  avClient                : AvClient,
  httpErrorHandler        : HttpErrorHandler,
  actorSystem             : akka.actor.ActorSystem,
  val applicationLifecycle: play.api.inject.ApplicationLifecycle,
  val configuration       : play.api.Configuration,
  val environment         : play.api.Environment
)(implicit
  val executionContext: scala.concurrent.ExecutionContext,
  val materializer    : akka.stream.Materializer
) extends AhcWSComponents {

  private val logger = Logger(getClass)

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

  lazy val testOnlySdesStubIsEnabled = servicesConfig.getConfBool("sdes-stub.enabled", defBool = false)

  lazy val optTestOnlySdesStubBaseUrl = if (testOnlySdesStubIsEnabled) Some(servicesConfig.baseUrl("sdes-stub")) else None


  val subscribe: (ActorRef, Class[_]) => Boolean = actorSystem.eventStream.subscribe

  val publish: (AnyRef) => Unit = actorSystem.eventStream.publish

  val now: () => Long = () => System.currentTimeMillis()

  lazy val commandHandler: CommandHandler = new CommandHandlerImpl(auditedHttpExecute, fileUploadBackendBaseUrl, wsClient, publish)

  lazy val getFileLength =
    (envelopeId: EnvelopeId, fileId: FileId, version: FileRefId) =>
      s3Service.getFileLengthFromQuarantine(createS3Key(envelopeId, fileId), version.value)

  // scanner
  actorSystem.actorOf(ScannerActor.props(subscribe, scanBinaryData, commandHandler), "scannerActor")
  actorSystem.actorOf(TransferActor.props(subscribe, createS3Key, commandHandler, getFileLength, s3Service.copyFromQtoT), "transferActor")
  actorSystem.actorOf(DeletionActor.props(subscribe, deleteObjectFromQuarantineBucket, createS3Key), "deletionActor")


  //lazy val retrieveFileFromQuarantineBucket = s3Service.retrieveFileFromQuarantine _
  //TODO discuss alternative thread pools
  lazy val getFileFromQuarantine = {
    lazy val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(25))
    QuarantineService.getFileFromQuarantine(s3Service.retrieveFileFromQuarantine)(_: S3KeyName, _: String)(ec)
  }

  // auditing
  lazy val auditedHttpExecute = PlayHttp.execute(auditConnector, "file-upload-frontend", Some(t => logger.warn(t.getMessage, t))) _

  // transfer
  lazy val envelopeResult = transfer.Repository.envelopeDetail(auditedHttpExecute, fileUploadBackendBaseUrl, wsClient) _

  lazy val numberOfTimeoutAttempts: Int = configuration.getOptional[Int](s"clam.antivirus.numberOfTimeoutAttempts").getOrElse(1)

  lazy val scanner: AvScan = new VirusScanner(avClient).scan

  lazy val scanBinaryData: (EnvelopeId, FileId, FileRefId) => Future[ScanResult] = {
    val disableScanning = configuration.getOptional[Boolean](s"clam.antivirus.disableScanning").getOrElse(false)
    if (disableScanning) (_: EnvelopeId, _: FileId, _: FileRefId) => Future.successful(Right(ScanResultFileClean))
    else ScanningService.scanBinaryData(scanner, numberOfTimeoutAttempts, getFileFromQuarantine)(createS3Key)
  }

  lazy val withValidEnvelope = EnvelopeChecker.withValidEnvelope(envelopeResult) _
}
