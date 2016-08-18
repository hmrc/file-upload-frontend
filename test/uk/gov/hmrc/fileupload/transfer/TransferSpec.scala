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
import uk.gov.hmrc.fileupload.DomainFixtures._
import uk.gov.hmrc.fileupload.ServiceConfig
import uk.gov.hmrc.fileupload.transfer.TransferService.{EnvelopeAvailableServiceError, EnvelopeNotFoundError}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class TransferSpec extends UnitSpec with ScalaFutures with WithFakeApplication with FakeFileUploadBackend {

  override lazy val fileUploadBackendPort = new URL(ServiceConfig.fileUploadBackendBaseUrl).getPort

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(1, Second))

  "When calling the envelope check" should {

     val envelopeAvailable = TransferService.envelopeAvailable(_.execute().map(response => Xor.Right(response)), ServiceConfig.fileUploadBackendBaseUrl) _

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
}
