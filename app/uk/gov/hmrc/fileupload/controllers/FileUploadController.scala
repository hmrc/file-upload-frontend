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

import java.nio.file.Files

import cats.data.Xor
import play.api.libs.Files.TemporaryFile
import play.api.mvc._
import uk.gov.hmrc.fileupload.controllers.FileUploadController.validateRequest
import uk.gov.hmrc.fileupload.upload.Service.{UploadRequestError, UploadResult, UploadServiceError}
import uk.gov.hmrc.fileupload.{EnvelopeId, File, FileId}

import scala.concurrent.{ExecutionContext, Future}

class FileUploadController(uploadFile: File => Future[UploadResult])
                          (implicit executionContext: ExecutionContext) {


  def upload() = Action.async(BodyParsers.parse.multipartFormData) { implicit request =>
    validateRequest(request).map(uploadFile.andThen(_.map {
      case Xor.Left(UploadServiceError(_, message)) => Results.InternalServerError(message)
      case Xor.Left(UploadRequestError(_, message)) => Results.BadRequest(message)
      case Xor.Right(_) => Results.Ok
    })).fold(message => Future.successful(Results.BadRequest(message)), identity)
  }
}

object FileUploadController {

  def validateRequest(request: Request[MultipartFormData[TemporaryFile]]): Xor[String, File] = {

    def extract(parameter: String) = Xor.fromOption(request.body.dataParts.get(parameter).flatMap(_.headOption), s"Missing $parameter")

    for {
      fileData <- Xor.fromOption(request.body.files.headOption.map(f => (f.ref, f.filename, f.contentType)), "Missing file data")
      envelopeId <- extract("envelopeId")
      fileID <- extract("fileId")
    } yield File(Files.readAllBytes(fileData._1.file.toPath), fileData._2, fileData._3, EnvelopeId(envelopeId), FileId(fileID))
  }
}
