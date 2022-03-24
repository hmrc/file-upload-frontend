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

package uk.gov.hmrc.fileupload.controllers

import javax.inject.{Inject, Singleton}
import org.slf4j.MDC
import play.api.Configuration
import play.api.Logger
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.mvc.{Action, EssentialAction, MessagesControllerComponents, MaxSizeExceeded, MultipartFormData, Result}
import uk.gov.hmrc.fileupload.{ApplicationModule, EnvelopeId, FileId, FileRefId}
import uk.gov.hmrc.fileupload.controllers.EnvelopeChecker.WithValidEnvelope
import uk.gov.hmrc.fileupload.controllers.FileUploadController.metadataAsJson
import uk.gov.hmrc.fileupload.controllers.EnvelopeChecker.getMaxFileSizeFromEnvelope
import uk.gov.hmrc.fileupload.notifier.{CommandHandler, QuarantineFile}
import uk.gov.hmrc.fileupload.quarantine.EnvelopeConstraints
import uk.gov.hmrc.fileupload.s3.InMemoryMultipartFileHandler.{cacheFileInMemory, FileCachedInMemory}
import uk.gov.hmrc.fileupload.s3.{S3KeyName, S3Service}
import uk.gov.hmrc.fileupload.utils.StreamImplicits._
import uk.gov.hmrc.fileupload.utils.{LoggerHelper, LoggerValues, errorAsJson}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileUploadController @Inject()(
  appModule: ApplicationModule,
  config   : Configuration,
  mcc      : MessagesControllerComponents
)(implicit
  executionContext: ExecutionContext
) extends FrontendController(mcc) {

  private val logger = Logger(getClass)

  val redirectionFeature: RedirectionFeature                = appModule.redirectionFeature
  val withValidEnvelope : WithValidEnvelope                 = appModule.withValidEnvelope
  val commandHandler    : CommandHandler                    = appModule.commandHandler
  val uploadToQuarantine: S3Service.UploadToQuarantine      = appModule.uploadToQuarantine
  val createS3Key       : (EnvelopeId, FileId) => S3KeyName = appModule.createS3Key
  val now               : () => Long                        = appModule.now
  val loggerHelper      : LoggerHelper                      = appModule.loggerHelper

  private lazy val logFileExtensions: Boolean =
    config.getOptional[Boolean]("flags.log-file-extensions").getOrElse(false)

  def uploadWithRedirection(
    envelopeId            : EnvelopeId,
    fileId                : FileId,
    `redirect-success-url`: Option[String],
    `redirect-error-url`  : Option[String],
  ): EssentialAction =
    redirectionFeature.redirect(`redirect-success-url`, `redirect-error-url`) {
      uploadWithEnvelopeValidation(envelopeId: EnvelopeId, fileId: FileId)
    }

  def uploadWithEnvelopeValidation(envelopeId: EnvelopeId, fileId: FileId): EssentialAction =
    withValidEnvelope(envelopeId) {
      setMaxFileSize => upload(setMaxFileSize)(envelopeId, fileId)
    }

  def upload(constraints: Option[EnvelopeConstraints])
            (envelopeId: EnvelopeId, fileId: FileId): Action[Either[MaxSizeExceeded, MultipartFormData[FileCachedInMemory]]] = {
    val maxSize = getMaxFileSizeFromEnvelope(constraints)
    Action.async(parse.maxLength(maxSize, parse.multipartFormData(cacheFileInMemory))) { implicit request =>
      implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
      request.body match {
        case Left(_) => Future.successful(EntityTooLarge)
        case Right(formData) =>
          val allowZeroLengthFiles = constraints.flatMap(_.allowZeroLengthFiles)
          val fileIsEmpty = formData.files.headOption.map(_.ref.size)

          val failedRequirementsO =
            if (formData.files.size != 1)
              Some(BadRequest(errorAsJson("Request must have exactly 1 file attached")))
            else if (allowZeroLengthFiles.contains(false) && fileIsEmpty.contains(0))
              Some(BadRequest(errorAsJson("Envelope does not allow zero length files, and submitted file has length 0")))
            else
              None

          failedRequirementsO match {
            case Some(failure) =>
              Future.successful(failure)
            case _ =>
              logger.info(s"Uploading $fileId to $envelopeId. allowZeroLengthFiles flag is $allowZeroLengthFiles, " +
                s"fileIsEmpty value is $fileIsEmpty.")
              val uploadResult = uploadTheProperFile(envelopeId, fileId, formData)
              if (logFileExtensions) {
                val loggerValues = loggerHelper.getLoggerValues(formData.files.head, request)
                logFileExtensionData(uploadResult)(loggerValues)
              } else {
                uploadResult
              }
          }
      }
    }
  }

  private def uploadTheProperFile(
    envelopeId   : EnvelopeId,
    fileId       : FileId,
    formData     : MultipartFormData[FileCachedInMemory]
  )(implicit
    hc: HeaderCarrier
  ) = {
    val file = formData.files.head
    val key = createS3Key(envelopeId, fileId)
    uploadToQuarantine(key, file.ref.inputStream, file.ref.size).flatMap { uploadResult =>
      val fileRefId = FileRefId(uploadResult.getVersionId)
      commandHandler.notify(
        QuarantineFile(
          id          = envelopeId, fileId,
          fileRefId   = fileRefId,
          created     = now(),
          name        = file.filename,
          contentType = file.contentType.getOrElse(""),
          length      = file.ref.size,
          metadata    = metadataAsJson(formData)
        )
      )
      .map {
        case Right(_) => Ok
        case Left(e) => Status(e.statusCode)(e.reason)
      }
    }
  }

  private def logFileExtensionData(upload: Future[Result])(values: LoggerValues) =
    try {
      MDC.put("upload-file-extension", values.fileExtension)
      MDC.put("upload-user-agent", values.userAgent)
      logger.info(s"Uploading file with file extension: [${values.fileExtension}] and user agent: [${values.userAgent}]")
      upload
    } finally {
      MDC.remove("upload-file-extension")
      MDC.remove("upload-user-agent")
    }
}

object FileUploadController {
  def metadataAsJson(formData: MultipartFormData[FileCachedInMemory]): JsObject = {
    val metadataParams = formData.dataParts.collect {
      case (key, singleValue :: Nil) => key -> JsString(singleValue)
      case (key, values: Seq[String]) if values.nonEmpty => key -> Json.toJson(values)
    }

    val metadata = if (metadataParams.nonEmpty) {
      Json.toJson(metadataParams).as[JsObject]
    } else {
      Json.obj()
    }
    metadata
  }
}
