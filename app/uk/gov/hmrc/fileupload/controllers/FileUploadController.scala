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
import cats.std.future._
import play.api.Logger
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.mvc.Results._
import play.api.mvc._
import uk.gov.hmrc.fileupload.controllers.FileUploadController._
import uk.gov.hmrc.fileupload.fileupload._
import uk.gov.hmrc.fileupload.quarantine.QuarantineService.QuarantineDownloadResult
import uk.gov.hmrc.fileupload.quarantine.Quarantined
import uk.gov.hmrc.fileupload.transfer.Repository.{SendMetadataEnvelopeNotFound, SendMetadataResult}
import uk.gov.hmrc.fileupload.upload.UploadService.{UploadResult, UploadServiceDownstreamError, UploadServiceEnvelopeNotFoundError}
import uk.gov.hmrc.fileupload.virusscan.ScanningService.{ScanResult, ScanResultVirusDetected}
import uk.gov.hmrc.fileupload.{EnvelopeId, File, FileId}

import scala.concurrent.{ExecutionContext, Future}

class FileUploadController(uploadParser: () => BodyParser[MultipartFormData[Future[JSONReadFile]]],
                           transferToTransient: (File, Request[_]) => Future[UploadResult],
                           getFileFromQuarantine: (EnvelopeId, FileId, Future[JSONReadFile]) => Future[QuarantineDownloadResult],
                           scanBinaryData: File => Future[ScanResult],
                           publish: AnyRef => Unit,
                           sendMetadata: (EnvelopeId, FileId, JsObject) => Future[SendMetadataResult])
                          (implicit executionContext: ExecutionContext) {

  def upload(envelopeId: EnvelopeId, fileId: FileId) = Action.async(uploadParser()) { implicit request =>
    publish(Quarantined(envelopeId, fileId))
    (for {
      _               <- xorT(sendMetadata(envelopeId, fileId, metadataAsJson))
      fileRef         <- XorT.fromXor(getFileRefFromRequest)
      fileForScanning <- xorT(getFileFromQuarantine(envelopeId, fileId, fileRef))
      _               <- xorT(scanBinaryData(fileForScanning))
      fileForTransfer <- xorT(getFileFromQuarantine(envelopeId, fileId, fileRef))
      _               <- xorT(transferToTransient(fileForTransfer, request))
    } yield {
      ()
    }).value.map {
      case Xor.Right(_) => Ok
      case Xor.Left(error) => errorToResult(error)
    }
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