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
import play.api.libs.json.{JsString, JsValue}
import play.api.mvc.{BodyParser, MultipartFormData}
import reactivemongo.json.JSONSerializationPack
import reactivemongo.json.JSONSerializationPack._
import uk.gov.hmrc.fileupload.DomainFixtures.anyFile
import uk.gov.hmrc.fileupload.RestFixtures._
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.fileupload._
import uk.gov.hmrc.fileupload.quarantine.FileData
import uk.gov.hmrc.fileupload.upload.Service._
import uk.gov.hmrc.fileupload.virusscan.ScanningService.{ScanResult, ScanResultFileClean}
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

  def newController(uploadParser: => () => BodyParser[MultipartFormData[Future[JSONReadFile]]] = parse,
                    uploadFile: File => Future[UploadResult] = _ => failed,
                    retrieveFile: (String) => Future[Option[FileData]] = _ => Future.successful(Some(FileData(0, null))),
                    scanBinaryData: File => Future[ScanResult] = _ => Future.successful(Xor.right(ScanResultFileClean)),
                    publish: (AnyRef) => Unit = _ => Unit) =
    new FileUploadController(uploadParser, uploadFile, retrieveFile, scanBinaryData, publish)

  "POST /upload" should {
    "return OK response if successfully upload files" in {
      val file = anyFile()

      val request = validUploadRequest(file)

      val uploadFile: (File) => Future[UploadResult] = {
        case File(_, _, _, _, file.envelopeId, file.fileId) => Future.successful(Xor.right(file.envelopeId))
        case unknownFile => fail(s"Trying to upload wrong file data [$unknownFile] expected [$file]")
      }

      val controller = newController(uploadFile = uploadFile)
      val result = controller.upload()(request).futureValue

      status(result) shouldBe Status.OK
    }

    "return INTERNAL_SERVER_ERROR response if service error when uploading files" in {
      val request = validUploadRequest()

      val controller = newController(uploadFile = file => Future.successful(Xor.left(UploadServiceDownstreamError(file.envelopeId, "something went wrong"))))
      val result = controller.upload()(request).futureValue

      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
    }

    "return NOT_FOUND response if envelope is not found" in {
      val request = validUploadRequest()

      val controller = newController(uploadFile = file => Future.successful(Xor.left(UploadServiceEnvelopeNotFoundError(file.envelopeId))))
      val result = controller.upload()(request).futureValue

      status(result) shouldBe Status.NOT_FOUND
    }

    Seq("fileId", "envelopeId") foreach {
      missingParam =>
        s"Bad request if missing $missingParam" in {
          val file = anyFile()
          val validRequest = validUploadRequest(file)
          val bodyMissingParameter: MultipartFormData[Future[JSONReadFile]] = MultipartFormData(validRequest.body.dataParts - missingParam,
            Seq(MultipartFormData.FilePart(file.filename, file.filename, file.contentType,
              Future.successful(TestJsonReadFile()))),
            Seq.empty, Seq.empty)

          val controller = newController(uploadFile = _ => Future.successful(Xor.right(file.envelopeId)))
          val result = controller.upload()(uploadRequest(bodyMissingParameter)).futureValue

          status(result) shouldBe Status.BAD_REQUEST
        }
    }
  }
}
