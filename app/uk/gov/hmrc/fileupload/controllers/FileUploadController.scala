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

package uk.gov.hmrc.fileupload.controllers

import cats.data.Xor
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.mvc._
import uk.gov.hmrc.fileupload.controllers.FileUploadController._
import uk.gov.hmrc.fileupload.fileupload._
import uk.gov.hmrc.fileupload.notifier.NotifierService._
import uk.gov.hmrc.fileupload.quarantine.FileInQuarantineStored
import uk.gov.hmrc.fileupload.transfer.TransferRequested
import uk.gov.hmrc.fileupload.transfer.TransferService.EnvelopeStatusResult
import uk.gov.hmrc.fileupload.utils.errorAsJson
import uk.gov.hmrc.fileupload.virusscan.VirusScanRequested
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}

import scala.concurrent.{ExecutionContext, Future}

class FileUploadController(envelopeStatus:(EnvelopeId) => Future[EnvelopeStatusResult],
                           uploadParser: () => BodyParser[MultipartFormData[Future[JSONReadFile]]],
                           notify: AnyRef => Future[NotifyResult],
                           now: () => Long)
                          (implicit executionContext: ExecutionContext) extends Controller {

  val MAX_FILE_SIZE_IN_BYTES = 1024 * 1024 * 11

  def upload(envelopeId: EnvelopeId, fileId: FileId) =
    Action.async(parse.maxLength(MAX_FILE_SIZE_IN_BYTES, uploadParser())) { implicit request =>
      request.body match {
        case Left(maxSizeExceeded) => Future.successful(EntityTooLarge)
        case Right(formData) =>
          val numberOfAttachedFiles = formData.files.size
          if (numberOfAttachedFiles == 1) {
            val file = formData.files.head
            file.ref.flatMap { fileRef =>
              val fileRefId = fileRef.id match {
                case JsString(value) => FileRefId(value)
                case _ => throw new Exception("invalid reference")
              }
              notify(FileInQuarantineStored(
                envelopeId, fileId, fileRefId, created = fileRef.uploadDate.getOrElse(now()), name = file.filename,
                contentType = file.contentType.getOrElse(""), metadata = metadataAsJson(formData))) map {
                case Xor.Right(_) => Ok
                case Xor.Left(e) => Result(ResponseHeader(e.statusCode), Enumerator(e.reason.getBytes))
              }
            }
          } else {
            Future.successful(BadRequest(errorAsJson("Request must have exactly 1 file attached")))
          }
      }
    }

  def scan(envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId) = Action.async { request =>
    notify(VirusScanRequested(envelopeId = envelopeId, fileId = fileId, fileRefId = fileRefId))
    Future.successful(Ok)
  }

  def transfer(envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId) = Action.async { request =>
    notify(TransferRequested(envelopeId = envelopeId, fileId = fileId, fileRefId = fileRefId))
    Future.successful(Ok)
  }

}

object FileUploadController {

  def metadataAsJson(formData: MultipartFormData[Future[JSONReadFile]]): JsObject = {
    val metadataParams = formData.dataParts.collect {
      case (key, singleValue :: Nil) => key -> JsString(singleValue)
      case (key, values: Seq[String]) if values.nonEmpty => key -> Json.toJson(values)
    }

    val metadata = if(metadataParams.nonEmpty) {
      Json.toJson(metadataParams).as[JsObject]
    } else {
      Json.obj()
    }

    metadata
  }
}