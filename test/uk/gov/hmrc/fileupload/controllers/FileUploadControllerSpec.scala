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
import org.scalatestplus.play.OneServerPerSuite
import play.api.http.Status
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.mvc.{BodyParser, MultipartFormData}
import reactivemongo.json.JSONSerializationPack
import reactivemongo.json.JSONSerializationPack._
import uk.gov.hmrc.fileupload.DomainFixtures.anyFile
import uk.gov.hmrc.fileupload.RestFixtures._
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.fileupload._
import uk.gov.hmrc.fileupload.notifier.NotifierService.{NotifyResult, NotifySuccess}
import uk.gov.hmrc.fileupload.quarantine.QuarantineService.QuarantineDownloadResult
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class FileUploadControllerSpec extends UnitSpec with ScalaFutures with OneServerPerSuite {

  import scala.concurrent.ExecutionContext.Implicits.global

  val failed = Future.failed(new Exception("not good"))

  case class TestJsonReadFile(id: JsValue = JsString("testid")) extends JSONReadFile {
    val pack = JSONSerializationPack
    val contentType: Option[String] = None
    val filename: Option[String] = None
    val chunkSize: Int = 0
    val length: Long = 0
    val uploadDate: Option[Long] = None
    val md5: Option[String] = None
    val metadata: Document = null
  }

  def parse = () => UploadParser.parse(null) _

  type GetFileFromQuarantine = (EnvelopeId, FileId, Future[JSONReadFile]) => Future[QuarantineDownloadResult]

  def newController(uploadParser: => () => BodyParser[MultipartFormData[Future[JSONReadFile]]] = parse,
                    notify: AnyRef => Future[NotifyResult] = _ => Future.successful(Xor.right(NotifySuccess))) =
    new FileUploadController(uploadParser, notify)

  "POST /upload" should {
    "return OK response if successfully upload files" in {
      val file = anyFile()
      val request = validUploadRequest(file)
      val controller = newController()

      val result = controller.upload(EnvelopeId(), FileId())(request).futureValue

      status(result) shouldBe Status.OK
    }

    "return 400 Bad Request if file was not found in the request" in {
      pending
      val requestWithoutAFile = uploadRequest(MultipartFormData(Map(), Seq(), Seq.empty, Seq.empty))
      val controller = newController()

      val result = controller.upload(EnvelopeId(), FileId())(requestWithoutAFile)

      status(result) shouldBe Status.BAD_REQUEST
    }
  }

  "function metadataToJson" should {
    "convert params of a multipart/form-data request to a Json Object" in {
      val params = Map("foo" -> Seq("1"), "bar" -> Seq("2"))
      val req = multipartFormData(params)

      val result = FileUploadController.metadataAsJson(req)

      result shouldBe Json.obj("metadata" -> Json.obj("foo" -> "1", "bar" -> "2"))
    }
    "work for an empty set of params" in {
      val params: Map[String, Seq[String]] = Map()
      val req = multipartFormData(params)

      val result = FileUploadController.metadataAsJson(req)

      result shouldBe Json.obj()
    }
    "work for keys with no corresponding values" in {
      val params: Map[String, Seq[String]] = Map("foo" -> Seq())
      val req = multipartFormData(params)

      val result = FileUploadController.metadataAsJson(req)

      result shouldBe Json.obj()
    }
    "work for keys with multiple values" in {
      val params: Map[String, Seq[String]] = Map("foo" -> Seq("bar", "baz"))
      val req = multipartFormData(params)

      val result = FileUploadController.metadataAsJson(req)

      result shouldBe Json.obj("metadata" -> Json.obj("foo" -> List("bar", "baz")))
    }
    "include file name and contentType directly from a file part and rename `filename` -> `name`" in {
      val expectedFileName = "foo"
      val expectedContentType = Some("bar")
      val requestWithFileNameAndContentType = uploadRequest(MultipartFormData(Map(),
        Seq(MultipartFormData.FilePart("key", expectedFileName, expectedContentType, Future.successful(TestJsonReadFile()))),
        Seq.empty, Seq.empty))

      val result = FileUploadController.metadataAsJson(requestWithFileNameAndContentType)

      result shouldBe Json.obj("name" -> expectedFileName, "contentType" -> expectedContentType)
    }
  }
}
