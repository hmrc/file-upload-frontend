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
import com.amazonaws.services.s3.transfer.model.UploadResult
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData
import play.api.test.Helpers._
import uk.gov.hmrc.fileupload.DomainFixtures._
import uk.gov.hmrc.fileupload.RestFixtures._
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.notifier.CommandHandler
import uk.gov.hmrc.fileupload.notifier.NotifierService.NotifySuccess
import uk.gov.hmrc.fileupload.s3.InMemoryMultipartFileHandler
import uk.gov.hmrc.fileupload.s3.S3Service.UploadToQuarantine
import uk.gov.hmrc.fileupload.controllers.EnvelopeChecker._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class FileUploadControllerSpec extends UnitSpec with ScalaFutures with TestApplicationComponents {

  import scala.concurrent.ExecutionContext.Implicits.global

  val controller = {
    val noEnvelopeValidation = null
    val noParsingIsActuallyDoneHere = InMemoryMultipartFileHandler.parser
    val commandHandler = new CommandHandler {
      def notify(command: AnyRef)(implicit ec: ExecutionContext) = Future.successful(Xor.right(NotifySuccess))
    }
    val fakeCurrentTime = () => 10L
    val uploadToQuarantine: UploadToQuarantine = (_,_,_) => new UploadResult()
    val s3Key: (EnvelopeId, FileId) => String = (_,_) => "key"

    new FileUploadController(
      null,
      noEnvelopeValidation,
      noParsingIsActuallyDoneHere,
      commandHandler,
      uploadToQuarantine,
      s3Key,
      fakeCurrentTime
    )
  }

  "POST /upload" should {
    "return OK response if successfully upload files" in {
      val file = anyFile()
      val request = validUploadRequest(List(file))

      val result = controller.upload(defaultFileSize)(defaultContentTypes)(EnvelopeId(), FileId())(request)

      status(result) shouldBe Status.OK
    }

    "return 400 Bad Request if file was not found in the request" in {
      val requestWithoutAFile = uploadRequest(MultipartFormData(Map(), Seq(), Seq.empty), sizeExceeded = false)

      val result = controller.upload(defaultFileSize)(defaultContentTypes)(EnvelopeId(), FileId())(requestWithoutAFile)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe """{"error":{"msg":"Request must have exactly 1 file attached"}}"""
    }
    "return 400 Bad Request if >1 files were found in the request" in {
      val requestWith2Files = validUploadRequest(List(anyFile(), anyFile()))

      val result = controller.upload(defaultFileSize)(defaultContentTypes)(EnvelopeId(), FileId())(requestWith2Files)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe """{"error":{"msg":"Request must have exactly 1 file attached"}}"""
    }
    "return 413 Entity To Large if file size exceeds 10MB" in {
      val tooLargeRequest = validUploadRequest(List(anyFile()), sizeExceeded = true)

      val result = controller.upload(defaultFileSize)(defaultContentTypes)(EnvelopeId(), FileId())(tooLargeRequest)

      status(result) shouldBe Status.REQUEST_ENTITY_TOO_LARGE
    }
    "return 415 Unsupported File Type if file is not one of the specified content types" in {
      val file = anyInvalidFile()
      val unsupportedFileType = validUploadRequest(List(file))

      val result = controller.upload(defaultFileSize)(defaultContentTypes)(EnvelopeId(), FileId())(unsupportedFileType)

      status(result) shouldBe Status.UNSUPPORTED_MEDIA_TYPE
      contentAsString(result) shouldBe """{"error":{"msg":"Request must have exactly 1 file with a valid file type"}}"""
    }
  }

  "function metadataToJson" should {
    "convert params of a multipart/form-data request to a Json Object" in {
      val params = Map("foo" -> Seq("1"), "bar" -> Seq("2"))
      val formData = multipartFormData(params).body.right.get

      val result = FileUploadController.metadataAsJson(formData)

      result shouldBe Json.obj("foo" -> "1", "bar" -> "2")
    }
    "work for an empty set of params" in {
      val params: Map[String, Seq[String]] = Map()
      val formData = multipartFormData(params).body.right.get

      val result = FileUploadController.metadataAsJson(formData)

      result shouldBe Json.obj()
    }
    "work for keys with no corresponding values" in {
      val params: Map[String, Seq[String]] = Map("foo" -> Seq())
      val formData = multipartFormData(params).body.right.get

      val result = FileUploadController.metadataAsJson(formData)

      result shouldBe Json.obj()
    }
    "work for keys with multiple values" in {
      val params: Map[String, Seq[String]] = Map("foo" -> Seq("bar", "baz"))
      val formData = multipartFormData(params).body.right.get

      val result = FileUploadController.metadataAsJson(formData)

      result shouldBe Json.obj("foo" -> List("bar", "baz"))
    }
  }
}