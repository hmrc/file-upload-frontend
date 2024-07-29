/*
 * Copyright 2023 HM Revenue & Customs
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

import org.apache.pekko.actor.ActorRef
import play.api.Logger
import play.api.http.HttpErrorHandler
import play.api.libs.ws.ahc.AhcWSComponents
import uk.gov.hmrc.fileupload.controllers.{EnvelopeChecker, RedirectionFeature}
import uk.gov.hmrc.fileupload.infrastructure.PlayHttp
import uk.gov.hmrc.fileupload.notifier.{CommandHandler, CommandHandlerImpl}
import uk.gov.hmrc.fileupload.quarantine.QuarantineService
import uk.gov.hmrc.fileupload.s3.{AwsConfig, S3Service, S3Key, S3KeyName}
import uk.gov.hmrc.fileupload.s3.S3Service.DeleteFileFromQuarantineBucket
import uk.gov.hmrc.fileupload.transfer.TransferActor
import uk.gov.hmrc.fileupload.utils.{LoggerHelper, LoggerHelperFileExtensionAndUserAgent}
import uk.gov.hmrc.fileupload.virusscan.{AvClient, DeletionActor, ScannerActor, ScanningService, VirusScanner}
import uk.gov.hmrc.fileupload.virusscan.ScanningService.{AvScan, ScanResult, ScanResultFileClean}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.util.concurrent.Executors
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}


@Singleton
class ApplicationModule @Inject()(
  servicesConfig                   : ServicesConfig,
  auditConnector                   : AuditConnector,
  avClient                         : AvClient,
  s3Service                        : S3Service,
  awsConfig                        : AwsConfig,
  httpErrorHandler                 : HttpErrorHandler,
  actorSystem                      : org.apache.pekko.actor.ActorSystem,
  override val applicationLifecycle: play.api.inject.ApplicationLifecycle,
  override val configuration       : play.api.Configuration,
  override val environment         : play.api.Environment
)(using
  override val executionContext    : scala.concurrent.ExecutionContext,
  override val materializer        : org.apache.pekko.stream.Materializer
) extends AhcWSComponents {

  private val logger = Logger(getClass)

  lazy val downloadFromTransient =
    s3Service.downloadFromTransient

  lazy val uploadToQuarantine =
    s3Service.uploadToQuarantine

  lazy val deleteObjectFromQuarantineBucket: DeleteFileFromQuarantineBucket =
    s3Service.deleteObjectFromQuarantine

  lazy val createS3Key =
    S3Key.forEnvSubdir(awsConfig.envSubdir)

  val redirectionFeature =
    RedirectionFeature(configuration.underlying, httpErrorHandler)

  lazy val zipAndPresign =
    s3Service.zipAndPresign _

  val downloadFromQuarantine =
    s3Service.downloadFromQuarantine

  lazy val loggerHelper: LoggerHelper =
    LoggerHelperFileExtensionAndUserAgent()

  lazy val fileUploadBackendBaseUrl: String =
    servicesConfig.baseUrl("file-upload-backend")

  lazy val testOnlySdesStubIsEnabled: Boolean =
    servicesConfig.getConfBool("sdes-stub.enabled", defBool = false)

  lazy val optTestOnlySdesStubBaseUrl =
    if testOnlySdesStubIsEnabled then Some(servicesConfig.baseUrl("sdes-stub")) else None


  val subscribe: (ActorRef, Class[_]) => Boolean =
    actorSystem.eventStream.subscribe

  val publish: AnyRef => Unit =
    actorSystem.eventStream.publish

  val now: () => Long =
    () => System.currentTimeMillis()

  lazy val commandHandler: CommandHandler =
    CommandHandlerImpl(auditedHttpExecute, fileUploadBackendBaseUrl, wsClient, publish)

  lazy val getFileLength =
    (envelopeId: EnvelopeId, fileId: FileId, version: FileRefId) =>
      s3Service.getFileLengthFromQuarantine(createS3Key(envelopeId, fileId), version.value)

  // scanner
  actorSystem.actorOf(ScannerActor.props(subscribe, scanBinaryData, commandHandler), "scannerActor")
  actorSystem.actorOf(TransferActor.props(subscribe, createS3Key, commandHandler, getFileLength, s3Service.copyFromQtoT), "transferActor")
  actorSystem.actorOf(DeletionActor.props(subscribe, deleteObjectFromQuarantineBucket, createS3Key), "deletionActor")


  //TODO discuss alternative thread pools
  lazy val getFileFromQuarantine =
    lazy val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(25))
    QuarantineService.getFileFromQuarantine(s3Service.retrieveFileFromQuarantine)(_: S3KeyName, _: String)(using ec)

  // auditing
  lazy val auditedHttpExecute =
    PlayHttp.execute(auditConnector, "file-upload-frontend", Some(t => logger.warn(t.getMessage, t))) _

  // transfer
  lazy val envelopeResult =
    transfer.Repository.envelopeDetail(auditedHttpExecute, fileUploadBackendBaseUrl, wsClient) _

  lazy val numberOfTimeoutAttempts: Int =
    configuration.getOptional[Int](s"clam.antivirus.numberOfTimeoutAttempts").getOrElse(1)

  lazy val scanner: AvScan =
    VirusScanner(avClient).scan

  lazy val scanBinaryData: (EnvelopeId, FileId, FileRefId) => Future[ScanResult] =
    val disableScanning = configuration.getOptional[Boolean](s"clam.antivirus.disableScanning").getOrElse(false)
    if disableScanning then
      (_: EnvelopeId, _: FileId, _: FileRefId) => Future.successful(Right(ScanResultFileClean))
    else
      ScanningService.scanBinaryData(scanner, numberOfTimeoutAttempts, getFileFromQuarantine)(createS3Key)

  lazy val withValidEnvelope = EnvelopeChecker.withValidEnvelope(envelopeResult) _
}
