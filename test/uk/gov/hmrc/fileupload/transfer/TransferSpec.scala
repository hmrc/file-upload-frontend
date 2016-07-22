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
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.EnvelopeId
import uk.gov.hmrc.fileupload.transfer.Service.{EnvelopeAvailableEnvelopeNotFoundError, EnvelopeAvailableServiceError}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class TransferSpec extends UnitSpec with BeforeAndAfterAll with ScalaFutures with WithFakeApplication {

  private val fileUploadBacked = new WireMockServer(wireMockConfig().port(8080))

  override def beforeAll() = {
    super.beforeAll()
    fileUploadBacked.start()
  }

  override def afterAll() = {
    super.afterAll()
    fileUploadBacked.stop()
  }

  val lookup = Service.envelopeLookup("http://localhost:8080", HeaderCarrier()) _

  "When calling the envelope check" should {
    "if the ID is known of return a success" in {
      val envelopeId = EnvelopeId("1")

      respond(envelopeId, HTTP_OK)

      Service.envelopeAvailable(lookup)(envelopeId).futureValue shouldBe Xor.right(envelopeId)
    }

    "if the ID is not known of return an error" in {
      val envelopeId = EnvelopeId("2")

      respond(envelopeId, HTTP_NOT_FOUND)

      Service.envelopeAvailable(lookup)(envelopeId).futureValue shouldBe Xor.left(EnvelopeAvailableEnvelopeNotFoundError(envelopeId))
    }

    "if an error occurs return an error" in {
      val envelopeId = EnvelopeId("3")

      respond(envelopeId, HTTP_INTERNAL_ERROR, "SOME_ERROR")

      Service.envelopeAvailable(lookup)(envelopeId).futureValue shouldBe Xor.left(EnvelopeAvailableServiceError(envelopeId,
        """GET of 'http://localhost:8080/file-upload/envelope/3' returned 500. Response body: 'SOME_ERROR'"""))
    }
  }

  def respond(envelopeId: EnvelopeId, status: Int, body: String = ""): Unit = {
    fileUploadBacked.addStubMapping(
      get(urlPathMatching(s"/file-upload/envelope/${envelopeId.value}"))
        .willReturn(new ResponseDefinitionBuilder()
          .withBody(body)
          .withStatus(status))
        .build())
  }
}
