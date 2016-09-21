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
import play.api.mvc.Results._
import play.api.mvc._
import uk.gov.hmrc.fileupload.controllers.FileUploadController._
import uk.gov.hmrc.fileupload.fileupload._
import uk.gov.hmrc.fileupload.notifier.NotifierService._
import uk.gov.hmrc.fileupload.quarantine.FileInQuarantineStored
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}

import scala.concurrent.{ExecutionContext, Future}

class FileUploadController(uploadParser: () => BodyParser[MultipartFormData[Future[JSONReadFile]]],
                           notify: AnyRef => Future[NotifyResult],
                           now: () => Long)
                          (implicit executionContext: ExecutionContext) {

  def upload(envelopeId: EnvelopeId, fileId: FileId) = Action.async(uploadParser()) { implicit request =>
    request.body.files.headOption.map { file =>
      file.ref.flatMap { fileRef =>
        val fileRefId = fileRef.id match {
          case JsString(value) => FileRefId(value)
          case _ => throw new Exception("invalid reference")
        }
        notify(FileInQuarantineStored(
          envelopeId, fileId, fileRefId, created = fileRef.uploadDate.getOrElse(now()), name = file.filename, contentType = file.contentType.getOrElse(""), metadata = metadataAsJson)) map {
            case Xor.Right(_) => Ok
            case Xor.Left(e) => Result(ResponseHeader(e.statusCode), Enumerator(e.reason.getBytes))
        }
      }
    }.getOrElse(Future.successful(NotImplemented))
  }
}

object FileUploadController {

  def metadataAsJson(implicit request: Request[MultipartFormData[Future[JSONReadFile]]]): JsObject = {
    val metadataParams = request.body.dataParts.collect {
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