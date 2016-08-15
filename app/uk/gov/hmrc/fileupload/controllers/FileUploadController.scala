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
import play.api.Logger
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.JsString
import play.api.mvc._
import uk.gov.hmrc.fileupload.controllers.FileUploadController._
import uk.gov.hmrc.fileupload.fileupload._
import uk.gov.hmrc.fileupload.quarantine.FileData
import uk.gov.hmrc.fileupload.upload.Service.{UploadResult, UploadServiceDownstreamError, UploadServiceEnvelopeNotFoundError}
import uk.gov.hmrc.fileupload.virusscan.ScanningService.{ScanResult, ScanResultVirusDetected}
import uk.gov.hmrc.fileupload.{EnvelopeId, File, FileId}

import scala.concurrent.{ExecutionContext, Future}

class FileUploadController(uploadParser: () => BodyParser[MultipartFormData[Future[JSONReadFile]]],
                           transferToTransient: File => Future[UploadResult],
                           retrieveFile: (String) => Future[Option[FileData]],
                           scanBinaryData: Enumerator[Array[Byte]] => Future[ScanResult])
                          (implicit executionContext: ExecutionContext) {


  def upload() = Action.async(uploadParser()) { implicit request =>
    val maybeParams: Option[Parameters] = extractParams(request)

    maybeParams.flatMap { p =>

      request.body.files.headOption.map { fileInsideRequest =>

        (for {
          maybeFile <- getFileFromQuarantine(retrieveFile, p.envelopeId, p.fileId, fileInsideRequest.ref)
          file = maybeFile.getOrElse(throw new RuntimeException("File not found in quarantine"))
          scanResult <- scanBinaryData(file.data)
        } yield {
          scanResult match {
            case Xor.Right(_) =>
             for {
                maybeFile <- getFileFromQuarantine(retrieveFile, p.envelopeId, p.fileId, fileInsideRequest.ref)
                file = maybeFile.getOrElse(throw new RuntimeException("File not found in quarantine"))
                transferResult <- transferToTransient(file)
              } yield {
                transferResult match {
                  case Xor.Left(UploadServiceDownstreamError(_, message)) => Results.InternalServerError(message)
                  case Xor.Left(UploadServiceEnvelopeNotFoundError(_)) => Results.InternalServerError
                  case Xor.Right(_) => Results.Ok
                }
              }
            case Xor.Left(ScanResultVirusDetected) =>
              Logger.warn(s"Virus found!")
              Future.successful(Results.BadRequest("""{"message": "virus detected"}"""))
            case Xor.Left(otherProblem) =>
              Logger.warn(s"Problem with scanning: $otherProblem")
              Future.successful(Results.InternalServerError)
          }
        }).flatMap(identity)

      }
    }.getOrElse( /* missing params */ Future.successful(Results.BadRequest("""{"message": "no file received"}""")))
  }

}

object FileUploadController {

  case class Parameters(envelopeId: EnvelopeId, fileId: FileId)

  def extractParams(request: Request[MultipartFormData[Future[JSONReadFile]]]): Option[Parameters] = {
    val maybeEnvelopeId: Option[String] = request.body.dataParts.get("envelopeId").flatMap(_.headOption)
    val maybeFileId: Option[String] = request.body.dataParts.get("fileId").flatMap(_.headOption)

    for {
      envelopeId <- maybeEnvelopeId
      fileId <- maybeFileId
    } yield Parameters(envelopeId = EnvelopeId(envelopeId), fileId = FileId(fileId))
  }

  def getFileFromQuarantine(retrieveFile: (String) => Future[Option[FileData]],
                            envelopeId: EnvelopeId, fileId: FileId, eventualJsonReadFile: Future[JSONReadFile]): Future[Option[File]]= {
    import scala.concurrent.ExecutionContext.Implicits.global

    for {
      jsonReadFile <- eventualJsonReadFile
      optionalFileData <- retrieveFile(jsonReadFile.id.asInstanceOf[JsString].value)
    } yield {
      optionalFileData.map(fileData => File(fileData.data, fileData.length, jsonReadFile.filename.getOrElse(""), jsonReadFile.contentType, envelopeId, fileId))
    }
  }
}