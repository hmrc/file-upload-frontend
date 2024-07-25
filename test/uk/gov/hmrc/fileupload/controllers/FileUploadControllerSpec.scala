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

import com.amazonaws.services.s3.transfer.model.UploadResult
import org.apache.pekko.actor.ActorSystem
import org.mockito.Mockito.when
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.{MessagesControllerComponents, MultipartFormData, Request}
import play.api.test.Helpers._
import uk.gov.hmrc.fileupload.DomainFixtures._
import uk.gov.hmrc.fileupload.RestFixtures._
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.controllers.EnvelopeChecker._
import uk.gov.hmrc.fileupload.notifier.CommandHandler
import uk.gov.hmrc.fileupload.notifier.NotifierService.NotifySuccess
import uk.gov.hmrc.fileupload.quarantine.EnvelopeConstraints
import uk.gov.hmrc.fileupload.s3.InMemoryMultipartFileHandler.FileCachedInMemory
import uk.gov.hmrc.fileupload.s3.S3KeyName
import uk.gov.hmrc.fileupload.s3.S3Service.UploadToQuarantine
import uk.gov.hmrc.fileupload.utils.{LoggerHelper, LoggerValues}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class FileUploadControllerSpec
  extends AnyWordSpec
     with Matchers
     with MockitoSugar
     with EitherValues
     with ScalaFutures
     with TestApplicationComponents {

  import scala.concurrent.ExecutionContext.Implicits.global

  val controller = {
    val noEnvelopeValidation = null
    //val noParsingIsActuallyDoneHere = InMemoryMultipartFileHandler.parser
    val commandHandler = new CommandHandler {
      def notify(command: AnyRef)(implicit ec: ExecutionContext, hc: HeaderCarrier) = Future.successful(Right(NotifySuccess))
    }
    val fakeCurrentTime = () => 10L
    val uploadToQuarantine: UploadToQuarantine = (_,_,_) => Future.successful(new UploadResult())
    val createS3Key: (EnvelopeId, FileId) => S3KeyName = (_,_) => S3KeyName("key")
    val configuration = Configuration.from(Map.empty)
    val loggerHelper = new LoggerHelper {
      override def getLoggerValues(formData: MultipartFormData.FilePart[FileCachedInMemory], request: Request[_]): LoggerValues =
        LoggerValues("txt", "some-user-agent")
    }

    implicit val as: ActorSystem = ActorSystem()

    val appModule = mock[ApplicationModule]
    when(appModule.withValidEnvelope).thenReturn(noEnvelopeValidation)
    //when(appModule.inMemoryBodyParser).thenReturn(noParsingIsActuallyDoneHere)
    when(appModule.commandHandler).thenReturn(commandHandler)
    when(appModule.uploadToQuarantine).thenReturn(uploadToQuarantine)
    when(appModule.createS3Key).thenReturn(createS3Key)
    when(appModule.now).thenReturn(fakeCurrentTime)
    when(appModule.loggerHelper).thenReturn(loggerHelper)

    new FileUploadController(
      appModule,
      configuration,
      app.injector.instanceOf[MessagesControllerComponents]
    )
  }

  val defaultConstraints = EnvelopeConstraints(10, s"${defaultFileSize / 1024}KB", s"${defaultFileSize / 1024}KB", None)

  "POST /upload" should {
    "return OK response if successfully upload files" in {
      val file = anyFile()
      val request = validUploadRequest(List(file))

      val result = controller.upload(Some(defaultConstraints))(EnvelopeId(), FileId())(request)

      status(result) shouldBe Status.OK
    }

    "return 400 Bad Request if file has no contents and allowZeroLengthFiles is false" in {
      val request = emptyFileUploadRequest(filename = "foo")
      val constraints = Some(defaultConstraints.copy(allowZeroLengthFiles = Some(false)))
      val result = controller.upload(constraints)(EnvelopeId(), FileId())(request)

      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 200 OK if file has no contents and allowZeroLengthFiles is true" in {
      val request = emptyFileUploadRequest(filename = "foo")
      val constraints = Some(defaultConstraints.copy(allowZeroLengthFiles = Some(true)))
      val result = controller.upload(constraints)(EnvelopeId(), FileId())(request)

      status(result) shouldBe Status.OK
    }

    "return 200 OK if file has no contents and allowZeroLengthFiles is not set" in {
      val request = emptyFileUploadRequest(filename = "foo")
      val constraints = Some(defaultConstraints.copy(allowZeroLengthFiles = None))
      val result = controller.upload(constraints)(EnvelopeId(), FileId())(request)

      status(result) shouldBe Status.OK
    }

    "return 400 Bad Request if file was not found in the request" in {
      val requestWithoutAFile = uploadRequest(MultipartFormData(Map(), Seq(), Seq.empty), sizeExceeded = false)

      val result = controller.upload(Some(defaultConstraints))(EnvelopeId(), FileId())(requestWithoutAFile)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe """{"error":{"msg":"Request must have exactly 1 file attached"}}"""
    }

    "return 400 Bad Request if >1 files were found in the request" in {
      val requestWith2Files = validUploadRequest(List(anyFile(), anyFile()))

      val result = controller.upload(Some(defaultConstraints))(EnvelopeId(), FileId())(requestWith2Files)

      status(result) shouldBe Status.BAD_REQUEST
      contentAsString(result) shouldBe """{"error":{"msg":"Request must have exactly 1 file attached"}}"""
    }

    "return 413 Entity To Large if file size exceeds 10MB" in {
      val tooLargeRequest = validUploadRequest(List(anyFile()), sizeExceeded = true)

      val result = controller.upload(Some(defaultConstraints))(EnvelopeId(), FileId())(tooLargeRequest)

      status(result) shouldBe Status.REQUEST_ENTITY_TOO_LARGE
    }

    "return 200 if file is not one of the specified content types in envelope" in {
      val file = anyUnSupportedFile()
      val unsupportedFileType = validUploadRequest(List(file))

      val result = controller.upload(Some(defaultConstraints))(EnvelopeId(), FileId())(unsupportedFileType)

      status(result) shouldBe Status.OK
    }

    "upload file if browser content type for xml files return as text/xml" in {
      val file = anyXml()
      val supportedXMLFileType = validUploadRequest(List(file))

      val result = controller.upload(Some(defaultConstraints))(EnvelopeId(), FileId())(supportedXMLFileType)

      status(result) shouldBe Status.OK
    }
  }

  "function metadataToJson" should {
    "convert params of a multipart/form-data request to a Json Object" in {
      val params = Map("foo" -> Seq("1"), "bar" -> Seq("2"))
      val formData = multipartFormData(params).body.value

      val result = FileUploadController.metadataAsJson(formData)

      result shouldBe Json.obj("foo" -> "1", "bar" -> "2")
    }

    "work for an empty set of params" in {
      val params: Map[String, Seq[String]] = Map()
      val formData = multipartFormData(params).body.value

      val result = FileUploadController.metadataAsJson(formData)

      result shouldBe Json.obj()
    }

    "work for keys with no corresponding values" in {
      val params: Map[String, Seq[String]] = Map("foo" -> Seq())
      val formData = multipartFormData(params).body.value

      val result = FileUploadController.metadataAsJson(formData)

      result shouldBe Json.obj()
    }

    "work for keys with multiple values" in {
      val params: Map[String, Seq[String]] = Map("foo" -> Seq("bar", "baz"))
      val formData = multipartFormData(params).body.value

      val result = FileUploadController.metadataAsJson(formData)

      result shouldBe Json.obj("foo" -> List("bar", "baz"))
    }
  }
}
