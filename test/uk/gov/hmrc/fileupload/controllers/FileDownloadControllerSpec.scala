/*
 * Copyright 2023 HM Revenue & Customs
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

import org.apache.pekko.stream.Materializer
import org.mockito.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.FakeRequest
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.s3.{MissingFileException, S3KeyName, S3Service, ZipData}

import scala.concurrent.Future
import java.net.URL

class FileDownloadControllerSpec
  extends AnyWordSpecLike
     with Matchers
     with MockitoSugar
     with ScalaFutures
     with TestApplicationComponents {

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit lazy val mat = app.injector.instanceOf[Materializer]

  val appModule = mock[ApplicationModule]

  def controller(
    zipAndPresign: (EnvelopeId, List[(FileId, Option[String])]) => Future[ZipData] = (_,_) => Future.failed(sys.error("Behaviour not provided"))
  ) = {
    val fakeCurrentTime = () => 10L
    val download: S3Service.DownloadFromBucket =
      _ => None
    val createS3Key: (EnvelopeId, FileId) => S3KeyName =
      (_,_) => S3KeyName("key")

    when(appModule.downloadFromTransient ).thenReturn(download)
    when(appModule.createS3Key           ).thenReturn(createS3Key)
    when(appModule.now                   ).thenReturn(fakeCurrentTime)
    when(appModule.downloadFromQuarantine).thenReturn(download)
    when(appModule.zipAndPresign         ).thenReturn(zipAndPresign)

    new FileDownloadController(
      appModule,
      app.injector.instanceOf[MessagesControllerComponents]
    )
  }

  "POST /zip" should {
    "return OK response if successfully upload files" in {
      val request = FakeRequest("POST", "/zip")

      val zipAndPresign: (EnvelopeId, List[(FileId, Option[String])]) => Future[ZipData] =
        (_,_) => Future.successful(ZipData(
          name        = "name",
          size        = 100,
          md5Checksum = "checksum",
          url         = new URL("http://asd.com")
        ))

      val result =
        call(
          controller(zipAndPresign).zip(EnvelopeId()),
          request,
          Json.parse(
            """{"files": {"fileId1":"file1"}}"""
          )
        )

      status(result) shouldBe Status.OK
      contentAsJson(result) shouldBe Json.parse("""{"name":"name","size":100,"md5Checksum":"checksum","url":"http://asd.com"}""")
    }

    "return GONE response if successfully upload files" in {
      val message = "fileId1 missing"

      val zipAndPresign: (EnvelopeId, List[(FileId, Option[String])]) => Future[ZipData] =
        (_,_) => Future.failed(new MissingFileException(message))

      val request = FakeRequest("POST", "/zip")

      val result =
        call(
          controller(zipAndPresign).zip(EnvelopeId()),
          request,
          Json.parse(
            """{"files": {"fileId1":"file1"}}"""
          )
        )

      status(result) shouldBe Status.GONE
      contentAsString(result) shouldBe message
    }
  }
}
