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

import cats.data.Xor
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Second, Span}
import uk.gov.hmrc.fileupload.DomainFixtures._
import uk.gov.hmrc.fileupload.transfer.TransferService.{EnvelopeAvailableServiceError, EnvelopeNotFoundError}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class RepositorySpec extends UnitSpec with ScalaFutures with WithFakeApplication with FakeFileUploadBackend {


  "When calling the envelope check" should {

    val envelopeAvailable = Repository.envelopeAvailable(_.execute().map(response => Xor.Right(response)), fileUploadBackendBaseUrl) _

    "if the ID is known of return a success" in {
      val envelopeId = anyEnvelopeId

      Wiremock.respondToEnvelopeCheck(envelopeId, HTTP_OK)

      envelopeAvailable(envelopeId).futureValue shouldBe Xor.right(envelopeId)
    }

    "if the ID is not known of return an error" in {
      val envelopeId = anyEnvelopeId

      Wiremock.respondToEnvelopeCheck(envelopeId, HTTP_NOT_FOUND)

      envelopeAvailable(envelopeId).futureValue shouldBe Xor.left(EnvelopeNotFoundError(envelopeId))
    }

    "if an error occurs return an error" in {
      val envelopeId = anyEnvelopeId
      val errorBody = "SOME_ERROR"

      Wiremock.respondToEnvelopeCheck(envelopeId, HTTP_INTERNAL_ERROR, errorBody)

      envelopeAvailable(envelopeId).futureValue shouldBe Xor.left(EnvelopeAvailableServiceError(envelopeId, "SOME_ERROR"))
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
