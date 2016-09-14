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

import cats.data.{Xor, XorT}
import play.api.Logger
import play.api.libs.json.{JsObject, JsString, JsSuccess, Json}
import play.api.mvc.Results._
import play.api.mvc._
import uk.gov.hmrc.fileupload.controllers.FileUploadController._
import uk.gov.hmrc.fileupload.fileupload._
import uk.gov.hmrc.fileupload.quarantine.Quarantined
import uk.gov.hmrc.fileupload.transfer.Repository.{SendMetadataEnvelopeNotFound, SendMetadataResult}
import uk.gov.hmrc.fileupload.upload.UploadService.{UploadResult, UploadServiceDownstreamError, UploadServiceEnvelopeNotFoundError}
import uk.gov.hmrc.fileupload.virusscan.ScanningService.ScanResultVirusDetected
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileReferenceId}

import scala.concurrent.{ExecutionContext, Future}

class FileUploadController(uploadParser: () => BodyParser[MultipartFormData[Future[JSONReadFile]]],
                           transferToTransient: (FileReferenceId, Request[_]) => Future[UploadResult],
                           publish: AnyRef => Unit,
                           sendMetadata: (EnvelopeId, FileId, JsObject) => Future[SendMetadataResult])
                          (implicit executionContext: ExecutionContext) {

  def upload(envelopeId: EnvelopeId, fileId: FileId) = Action.async(uploadParser()) { implicit request =>
    request.body.files.headOption.map { file =>
      file.ref.map { fileRef =>
        val fileReferenceId = fileRef.id match {
          case JsString(value) => FileReferenceId(value)
          case _ => throw new Exception("invalid reference")
        }
        publish(Quarantined(envelopeId, fileId, fileReferenceId, name = file.filename, contentType = file.contentType.getOrElse(""), metadata = metadataAsJson))
      }
    }
    Future.successful(Ok)
  }

  private def xorT[T](v: Future[Xor[Any, T]]) = XorT.apply[Future, Any, T](v)

  private def getFileRefFromRequest(implicit request: Request[MultipartFormData[Future[JSONReadFile]]]) =
    Xor.fromOption(request.body.files.headOption.map(_.ref), ifNone = FileNotFoundInRequest)

  private def errorToResult(error: Any): Result =
    error match {
      case UploadServiceDownstreamError(_, message) => InternalServerError(msgAsJson(message))
      case UploadServiceEnvelopeNotFoundError(_) => NotFound(msgAsJson("Envelope not found"))
      case SendMetadataEnvelopeNotFound(_) => NotFound(msgAsJson("Envelope not found"))
      case FileNotFoundInRequest => BadRequest(msgAsJson("File not found in request"))
      case ScanResultVirusDetected =>
        Logger.warn(s"Virus found!")
        Ok
      case otherProblem =>
        Logger.warn(s"Error while uploading a file: $otherProblem")
        InternalServerError
    }

  private def msgAsJson(msg: String) = Json.toJson(Json.obj("message" -> msg))

  case object FileNotFoundInRequest
}

object FileUploadController {

  def metadataAsJson(implicit request: Request[MultipartFormData[Future[JSONReadFile]]]): JsObject = {
    val fileNameAndContentType = request.body.files.headOption.map { file =>
      Json.obj("name" -> file.filename) ++
        file.contentType.map(ct => Json.obj("contentType" -> ct)).getOrElse(Json.obj())
    }.getOrElse(Json.obj())

    val otherParams = request.body.dataParts.collect {
      case (key, singleValue :: Nil) => key -> JsString(singleValue)
      case (key, values: Seq[String]) if values.nonEmpty => key -> Json.toJson(values)
    }

    val metadata = if(otherParams.nonEmpty) {
      Json.obj("metadata" -> Json.toJson(otherParams))
    } else {
      Json.obj()
    }

    fileNameAndContentType ++ metadata
  }
}