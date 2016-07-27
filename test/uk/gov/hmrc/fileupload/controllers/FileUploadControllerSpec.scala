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
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData
import uk.gov.hmrc.fileupload.DomainFixtures.{anyFile, temporaryTexFile}
import uk.gov.hmrc.fileupload.RestFixtures._
import uk.gov.hmrc.fileupload.File
import uk.gov.hmrc.fileupload.upload.Service._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class FileUploadControllerSpec extends UnitSpec with ScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits.global

  "POST /upload" should {
    "return OK response if successfully upload files" in {
      val file = anyFile()

      val request = validUploadRequest(file)

      val uploadFile: (File) => Future[UploadResult] = {
        case File(_, _, _, file.envelopeId, file.fileId) => Future.successful(Xor.right(file.envelopeId))
        case unknownFile => fail(s"Trying to upload wrong file data [$unknownFile] expected [$file]")
      }

      val controller = new FileUploadController(uploadFile)
      val result = controller.upload()(request).futureValue

      status(result) shouldBe Status.OK
    }

    "return BAD_REQUEST response if request error when uploading files" in {
      val request = validUploadRequest()

      val controller = new FileUploadController(file => Future.successful(Xor.left(UploadRequestError(file.envelopeId, "that was a bad request"))))
      val result = controller.upload()(request).futureValue

      status(result) shouldBe Status.BAD_REQUEST
    }

    "return INTERNAL_SERVER_ERROR response if service error when uploading files" in {
      val request = validUploadRequest()

      val controller = new FileUploadController(file => Future.successful(Xor.left(UploadServiceError(file.envelopeId, "something went wrong"))))
      val result = controller.upload()(request).futureValue

      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
    }

    Seq("fileId", "envelopeId") foreach {
      missingParam =>
        s"Bad request if missing $missingParam" in {
          val file = anyFile()
          val validRequest = validUploadRequest(file)
          val bodyMissingParameter = MultipartFormData(validRequest.body.dataParts - missingParam,
            Seq(MultipartFormData.FilePart(file.filename, file.filename, file.contentType, TemporaryFile(temporaryTexFile()))),
            Seq.empty, Seq.empty)

          val controller = new FileUploadController(_ => Future.successful(Xor.right(file.envelopeId)))
          val result = controller.upload()(uploadRequest(bodyMissingParameter)).futureValue

          status(result) shouldBe Status.BAD_REQUEST
        }
    }

    s"Bad request if missing file data" in {
      val file = anyFile()
      val validRequest = validUploadRequest(file)
      val bodyMissingFileData: MultipartFormData[TemporaryFile] = MultipartFormData(validRequest.body.dataParts,
        Seq.empty, Seq.empty, Seq.empty)

      val controller = new FileUploadController(_ => Future.successful(Xor.right(file.envelopeId)))
      val result = controller.upload()(uploadRequest(bodyMissingFileData)).futureValue

      status(result) shouldBe Status.BAD_REQUEST
    }
  }
}
