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
import play.api.libs.iteratee.Enumerator
import play.api.mvc.MultipartFormData
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.fileupload.upload.Service.{UploadRequestError, UploadServiceError}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FileUploadControllerSpec extends UnitSpec with ScalaFutures {

  def createUploadRequest(): FakeRequest[MultipartFormData[Enumerator[Array[Byte]]]] = {
    val multipartBody = MultipartFormData[Enumerator[Array[Byte]]](Map.empty, Seq.empty, Seq.empty, Seq.empty)
    FakeRequest[MultipartFormData[Enumerator[Array[Byte]]]](method = "POST", uri = "/upload", headers = FakeHeaders(), body = multipartBody)
  }

  "POST /upload" should{
    "return OK response if successfully upload files" in {
      val request = createUploadRequest()

      val controller = new FileUploadController(() => Future.successful(Xor.right("")))
      val result = controller.upload()(request).futureValue

      status(result) shouldBe Status.OK
    }

    "return BAD_REQUEST response if request error when uploading files" in {
      val request = createUploadRequest()

      val controller = new FileUploadController(() => Future.successful(Xor.left(UploadRequestError("someId", "that was a bad request"))))
      val result = controller.upload()(request).futureValue

      status(result) shouldBe Status.BAD_REQUEST
    }

    "return INTERNAL_SERVER_ERROR response if service error when uploading files" in {
      val request = createUploadRequest()

      val controller = new FileUploadController(() => Future.successful(Xor.left(UploadServiceError("someId", "something went wrong"))))
      val result = controller.upload()(request).futureValue

      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
    }
  }
}
