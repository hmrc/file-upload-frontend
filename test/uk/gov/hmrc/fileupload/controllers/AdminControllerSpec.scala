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

import org.joda.time.DateTime
import org.scalatest.concurrent.ScalaFutures
import play.api.http.MimeTypes
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.fileupload.quarantine.FileInfo
import uk.gov.hmrc.fileupload.{FileRefId, TestApplicationComponents}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class AdminControllerSpec extends UnitSpec with ScalaFutures with TestApplicationComponents {

  implicit val ec = ExecutionContext.global

  val controller = {
    val getFileInfo = (refId: FileRefId) => Future.successful(Some(FileInfo("refId", "testFile", 2500, DateTime.parse("2016-11-29T12:27:19Z"), 10000, "application/content")))
    val getChunks = (x: FileRefId) => Future.successful(4)

    new AdminController(getFileInfo, getChunks)(null)
  }

  "GET /admin/files/info/ " should {
    "return FILE_INFO Json if file is stored in quarantine with expected no Chunks used" in {
      val expectedResponse = "{\"_id\":\"refId\",\"filename\":\"testFile\",\"chunkSize\":2500,\"uploadDate\":\"2016-11-29T12:27:19Z\",\"length\":10000,\"contentType\":\"application/content\"}"
      val result = controller.fileInfo(FileRefId("refId"))(FakeRequest())

      status(result) shouldBe 200
      contentType(result).get shouldBe MimeTypes.JSON
      contentAsString(result) shouldBe expectedResponse
    }
  }

  val controller_1 = {
    val getFileInfo = (refId: FileRefId) => Future.successful(None)
    val getChunks = (x: FileRefId) => Future.successful(4)

    new AdminController(getFileInfo, getChunks)(null)
  }

  "GET /admin/files/info/ " should {
    "return 404 Not Found for non-existent file in quarantine" in {
      val result = controller_1.fileInfo(FileRefId("refId"))(FakeRequest())

      status(result) shouldBe 404
    }
  }

  val controller_2 = {
    val getFileInfo = (refId: FileRefId) => Future.successful(Some(FileInfo("refId", "testFile", 2500, DateTime.parse("2016-11-29T12:27:19Z"), 10000, "application/content")))
    val getChunks = (x: FileRefId) => Future.successful(5)

    new AdminController(getFileInfo, getChunks)(null)
  }

  "GET /admin/files/info/ " should {
    "return 200 with error message about incorrect chunks used" in {
      val result = controller_2.fileInfo(FileRefId("refId"))(FakeRequest())
      val expectedNoChunks = 4d
      val actualNoChunks = 5

      status(result) shouldBe 200
      contentType(result).get shouldBe MimeTypes.JSON
      contentAsString(result) should include(s"Number of chunks expected $expectedNoChunks , actual $actualNoChunks")
    }
  }
}
