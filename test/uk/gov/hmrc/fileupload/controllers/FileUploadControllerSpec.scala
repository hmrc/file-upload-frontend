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

import play.api.http.Status
import play.api.libs.Files
import play.api.mvc.MultipartFormData.{BadPart, MissingFilePart}
import play.api.mvc.{MultipartFormData, Result}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future


class FileUploadControllerSpec extends UnitSpec with WithFakeApplication {

  def createUploadRequest(successRedirectURL:Option[String] = Some("http://somewhere.com/success"),
                          failureRedirectURL:Option[String] = Some("http://somewhere.com/failure"),
                          envelopeId:Option[String] = Some("1234567890"),
                          fileId:Option[String] = Some("1234567890")) = {
    var params = Map[String, Seq[String]]()

    if (successRedirectURL.isDefined) params = params + ("successRedirect" -> Seq(successRedirectURL.get))
    if (failureRedirectURL.isDefined) params = params + ("failureRedirect" -> Seq(failureRedirectURL.get))
    if (envelopeId.isDefined) params = params + ("envelopeId" -> Seq(envelopeId.get))
    if (fileId.isDefined) params = params + ("fileId" -> Seq(fileId.get))

    val multipartBody = MultipartFormData[Files.TemporaryFile](params, Seq(), Seq[BadPart](), Seq[MissingFilePart]())

    FakeRequest(method = "POST", uri = "/upload", headers = FakeHeaders(), body = multipartBody)
  }

  "POST /upload" should {
    "return 301 (redirect) to the `successRedirect` parameter if a valid request is received" in {
      val successRedirectURL = "http://somewhere.com/success"
      val fakeRequest = createUploadRequest(successRedirectURL = Some(successRedirectURL))

      val result: Future[Result] = FileUploadController.upload().apply(fakeRequest)
      status(result) shouldBe Status.MOVED_PERMANENTLY
      redirectLocation(result) shouldBe Some(successRedirectURL)
    }

    "return 400 (badRequest) if the `successRedirect` parameter is empty" in {
      val fakeRequest = createUploadRequest(successRedirectURL = Some(""))

      val result: Future[Result] = FileUploadController.upload().apply(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 400 (badRequest) if the `failureRedirect` parameter is empty" in {
      val fakeRequest = createUploadRequest(failureRedirectURL = Some(""))

      val result: Future[Result] = FileUploadController.upload().apply(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 400 (badRequest) if the `envelopeId` parameter is empty" in {
      val fakeRequest = createUploadRequest(envelopeId = Some(""))

      val result: Future[Result] = FileUploadController.upload().apply(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 400 (badRequest) if the `fileId` parameter is empty" in {
      val fakeRequest = createUploadRequest(fileId = Some(""))

      val result: Future[Result] = FileUploadController.upload().apply(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 400 (badRequest) if the `successRedirect` parameter is not present" in {
      val fakeRequest = createUploadRequest(successRedirectURL = None)

      val result: Future[Result] = FileUploadController.upload().apply(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 400 (badRequest) if the `failureRedirect` parameter is not present" in {
      val fakeRequest = createUploadRequest(failureRedirectURL = None)

      val result: Future[Result] = FileUploadController.upload().apply(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 400 (badRequest) if the `envelopeId` parameter is not present" in {
      val fakeRequest = createUploadRequest(envelopeId = None)

      val result: Future[Result] = FileUploadController.upload().apply(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 400 (badRequest) if the `fileId` parameter is not present" in {
      val fakeRequest = createUploadRequest(fileId = None)

      val result: Future[Result] = FileUploadController.upload().apply(fakeRequest)
      status(result) shouldBe Status.BAD_REQUEST
    }


/*
    "return 301 (redirect) to the `failureRedirect` parameter if a failure occurs during processing" in {
      val successRedirectURL = "http://somewhere.com/success"
      val failureRedirectURL = "http://somewhere.com/failure"
      val params = Map[String, Seq[String]]("successRedirect" -> Seq(successRedirectURL), "failureRedirect" -> failureRedirectURL)
      val multipartBody = MultipartFormData[Files.TemporaryFile](params, Seq(), Seq[BadPart](), Seq[MissingFilePart]())
      val fakeRequest = FakeRequest(method = "POST", uri = "/upload", headers = FakeHeaders(), body = multipartBody)

      val result: Future[Result] = FileUploadController.upload().apply(fakeRequest)
      status(result) shouldBe Status.MOVED_PERMANENTLY
      redirectLocation(result) shouldBe Some(successRedirectURL)
    }
*/


  }
}
