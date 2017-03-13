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
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.mvc._
import uk.gov.hmrc.fileupload.controllers.EnvelopeChecker.WithValidEnvelope
import uk.gov.hmrc.fileupload.controllers.FileUploadController._
import uk.gov.hmrc.fileupload.notifier.NotifierService._
import uk.gov.hmrc.fileupload.quarantine.FileInQuarantineStored
import uk.gov.hmrc.fileupload.s3.InMemoryMultipartFileHandler.{FileCachedInMemory, InMemoryMultiPartBodyParser}
import uk.gov.hmrc.fileupload.s3.S3Service.UploadToQuarantine
import uk.gov.hmrc.fileupload.utils.StreamImplicits.materializer
import uk.gov.hmrc.fileupload.utils.errorAsJson
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}

import scala.concurrent.{ExecutionContext, Future}

class FileUploadController(withValidEnvelope: WithValidEnvelope,
                           uploadParser: InMemoryMultiPartBodyParser,
                           notify: AnyRef => Future[NotifyResult],
                           uploadToQuarantine: UploadToQuarantine,
                           now: () => Long)
                          (implicit executionContext: ExecutionContext) extends Controller {

  val MAX_FILE_SIZE_IN_BYTES = 1024 * 1024 * 11

  def uploadWithEnvelopeValidation(envelopeId: EnvelopeId, fileId: FileId) =
    withValidEnvelope(envelopeId) {
      upload(envelopeId, fileId)
    }

  def upload(envelopeId: EnvelopeId, fileId: FileId) =
    Action.async(parse.maxLength(MAX_FILE_SIZE_IN_BYTES, uploadParser())) { implicit request =>
      request.body match {
        case Left(maxSizeExceeded) => Future.successful(EntityTooLarge)
        case Right(formData) =>
          val numberOfAttachedFiles = formData.files.size
          if (numberOfAttachedFiles == 1) {
            val file = formData.files.head
            uploadToQuarantine(fileId.value, file.ref.inputStream, file.ref.size).flatMap { uploadResult =>
              val fileRefId = FileRefId(uploadResult.getVersionId)
              notify(FileInQuarantineStored(
                envelopeId, fileId, fileRefId, created = now(), name = file.filename,
                contentType = file.contentType.getOrElse(""), metadata = metadataAsJson(formData))) map {
                case Xor.Right(_) => Ok
                case Xor.Left(e) => Status(e.statusCode)(e.reason)
              }
            }
          } else {
            Future.successful(BadRequest(errorAsJson("Request must have exactly 1 file attached")))
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
