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

package uk.gov.hmrc.fileupload.controllers

import cats.data.Xor
import play.api.Logger
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.mvc._
import uk.gov.hmrc.fileupload.controllers.EnvelopeChecker.WithValidEnvelope
import uk.gov.hmrc.fileupload.controllers.FileUploadController._
import uk.gov.hmrc.fileupload.controllers.EnvelopeChecker._
import uk.gov.hmrc.fileupload.notifier.{CommandHandler, QuarantineFile}
import uk.gov.hmrc.fileupload.quarantine.EnvelopeConstraints
import uk.gov.hmrc.fileupload.s3.InMemoryMultipartFileHandler.{FileCachedInMemory, InMemoryMultiPartBodyParser}
import uk.gov.hmrc.fileupload.s3.S3Service.UploadToQuarantine
import uk.gov.hmrc.fileupload.utils.StreamImplicits.materializer
import uk.gov.hmrc.fileupload.utils.errorAsJson
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}

import scala.concurrent.{ExecutionContext, Future}

class FileUploadController( redirectionFeature: RedirectionFeature,
                            withValidEnvelope: WithValidEnvelope,
                            uploadParser: InMemoryMultiPartBodyParser,
                            commandHandler: CommandHandler,
                            uploadToQuarantine: UploadToQuarantine,
                            createS3Key: (EnvelopeId, FileId) => String,
                            now: () => Long)
                          (implicit executionContext: ExecutionContext) extends Controller {

  def uploadWithRedirection(envelopeId: EnvelopeId, fileId: FileId,
                            `redirect-success-url`: Option[String], `redirect-error-url`: Option[String]): EssentialAction = {
    redirectionFeature.redirect(`redirect-success-url`, `redirect-error-url`) {
      uploadWithEnvelopeValidation(envelopeId: EnvelopeId, fileId: FileId)
    }
  }

  def uploadWithEnvelopeValidation(envelopeId: EnvelopeId, fileId: FileId): EssentialAction =
    withValidEnvelope(envelopeId) {
      setMaxFileSize => upload(setMaxFileSize)(envelopeId, fileId)
    }

  def upload(constraints: Option[EnvelopeConstraints])
            (envelopeId: EnvelopeId, fileId: FileId): Action[Either[MaxSizeExceeded, MultipartFormData[FileCachedInMemory]]] = {
    val maxSize = getMaxFileSizeFromEnvelope(constraints)
    Action.async(parse.maxLength(maxSize, uploadParser())) { implicit request =>
      request.body match {
        case Left(_) => Future.successful(EntityTooLarge)
        case Right(formData) =>
          val allowZeroLengthFiles = constraints.flatMap(_.allowZeroLengthFiles)
          val fileIsEmpty = formData.files.headOption.map(_.ref.size)

          val failedRequirementsO =
            if(formData.files.size != 1) Some(
                BadRequest(errorAsJson(
                  "Request must have exactly 1 file attached"
              )))
            else if (allowZeroLengthFiles.contains(false) && fileIsEmpty.contains(0)) Some(
              BadRequest(errorAsJson("Envelope does not allow zero length files, and submitted file has length 0"))
            )
            else None

          failedRequirementsO match {
            case Some(failure) =>
              Future.successful(failure)
            case _ =>
              Logger.info(s"Uploading $fileId to $envelopeId. allowZeroLengthFiles flag is $allowZeroLengthFiles, fileIsEmpty value is $fileIsEmpty.")
              uploadTheProperFile(envelopeId, fileId, formData)
          }
      }
    }
  }

  private def uploadTheProperFile(envelopeId: EnvelopeId, fileId: FileId, formData: MultipartFormData[FileCachedInMemory]) = {
    val file = formData.files.head
    val key = createS3Key(envelopeId, fileId)
    uploadToQuarantine(key, file.ref.inputStream, file.ref.size).flatMap { uploadResult =>
      val fileRefId = FileRefId(uploadResult.getVersionId)
      commandHandler.notify(QuarantineFile(envelopeId, fileId, fileRefId, created = now(), name = file.filename,
        contentType = file.contentType.getOrElse(""), file.ref.size, metadata = metadataAsJson(formData)))
        .map {
          case Xor.Right(_) => Ok
          case Xor.Left(e) => Status(e.statusCode)(e.reason)
        }
    }
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
