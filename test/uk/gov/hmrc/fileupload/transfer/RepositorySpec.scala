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

package uk.gov.hmrc.fileupload.transfer

import java.net.HttpURLConnection._
import java.net.URL

import cats.data.Xor
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Second, Span}
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.DomainFixtures._
import uk.gov.hmrc.fileupload.infrastructure.PlayHttp.PlayHttpError
import uk.gov.hmrc.fileupload.transfer.Repository.{SendMetadataEnvelopeNotFound, SendMetadataOtherError, SendMetadataSuccess}
import uk.gov.hmrc.fileupload.transfer.TransferService.{EnvelopeAvailableServiceError, EnvelopeNotFoundError}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, ServiceConfig}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RepositorySpec extends UnitSpec with ScalaFutures with WithFakeApplication with FakeFileUploadBackend {

  override lazy val fileUploadBackendPort = new URL(ServiceConfig.fileUploadBackendBaseUrl).getPort

  "When calling the envelope check" should {

    val envelopeAvailable = Repository.envelopeAvailable(_.execute().map(response => Xor.Right(response)), ServiceConfig.fileUploadBackendBaseUrl) _

    "if the ID is known of return a success" in {
      val envelopeId = anyEnvelopeId

      respondToEnvelopeCheck(envelopeId, HTTP_OK)

      envelopeAvailable(envelopeId).futureValue shouldBe Xor.right(envelopeId)
    }

    "if the ID is not known of return an error" in {
      val envelopeId = anyEnvelopeId

      respondToEnvelopeCheck(envelopeId, HTTP_NOT_FOUND)

      envelopeAvailable(envelopeId).futureValue shouldBe Xor.left(EnvelopeNotFoundError(envelopeId))
    }

    "if an error occurs return an error" in {
      val envelopeId = anyEnvelopeId
      val errorBody = "SOME_ERROR"

      respondToEnvelopeCheck(envelopeId, HTTP_INTERNAL_ERROR, errorBody)

      envelopeAvailable(envelopeId).futureValue shouldBe Xor.left(EnvelopeAvailableServiceError(envelopeId, "SOME_ERROR"))
    }
  }

  "Sending metadata" should {

    val sendMetadata = Repository.sendMetadata(_.execute().map(response => Xor.Right(response)), ServiceConfig.fileUploadBackendBaseUrl) _

    "be successful (happy path)" in {
      val envelopeId = EnvelopeId()
      val fileId = FileId()
      val metadata = Json.obj("foo" -> "bar")
      stubResponseForSendMetadata(envelopeId, fileId, metadata, Status.OK)

      val result = sendMetadata(envelopeId, fileId, metadata)

      result.futureValue shouldBe Xor.Right(SendMetadataSuccess)
    }
    "error for nonexistent envelopeId" in {
      val envelopeId = EnvelopeId()
      val fileId = FileId()
      val metadata = Json.obj("foo" -> "bar")
      val status = Status.NOT_FOUND
      stubResponseForSendMetadata(envelopeId, fileId, metadata, status)

      val result = sendMetadata(envelopeId, fileId, metadata)

      result.futureValue shouldBe Xor.Left(SendMetadataEnvelopeNotFound(envelopeId))
    }
    "return details of other errors" in {
      val envelopeId = EnvelopeId()
      val fileId = FileId()
      val metadata = Json.obj("foo" -> "bar")
      val errorMsg = "Internal Server Error (something broke)"
      val status = Status.INTERNAL_SERVER_ERROR
      stubResponseForSendMetadata(envelopeId, fileId, metadata, status, errorMsg)

      val result = sendMetadata(envelopeId, fileId, metadata)

      result.futureValue shouldBe Xor.Left(SendMetadataOtherError(s"Status: $status, body: $errorMsg"))
    }
    "error if auditedCall failed" in {
      val envelopeId = EnvelopeId()
      val fileId = FileId()
      val metadata = Json.obj("foo" -> "bar")

      val sendDataWithFailingAuditing = Repository.sendMetadata(
        _ => Future.successful(Xor.left(PlayHttpError("foo"))),
        ServiceConfig.fileUploadBackendBaseUrl) _

      val result = sendDataWithFailingAuditing(envelopeId, fileId, metadata)

      result.futureValue shouldBe Xor.Left(SendMetadataOtherError("foo"))
    }
  }

//  val transfer = Service.transfer(_.execute().map(response => Xor.Right(response)), ServiceConfig.fileUploadBackendBaseUrl) _
//
//  "When uploading a file" should {
//    "be successful if file uploaded" in {
//      val envelopeId = anyEnvelopeId
//      val fileId = anyFileId
//
//      responseToUpload(envelopeId, fileId, 200)
//
//      transfer(anyFileFor(envelopeId, fileId)).futureValue shouldBe Xor.right(envelopeId)
//    }
//
//    "give an error if file uploaded" in {
//      val envelopeId = anyEnvelopeId
//      val fileId = anyFileId
//
//      responseToUpload(envelopeId, fileId, 500, "SOME_ERROR")
//
//      transfer(anyFileFor(envelopeId, fileId)).futureValue shouldBe Xor.left(TransferServiceError(envelopeId, "SOME_ERROR"))
//    }
//  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(1, Second))
}
