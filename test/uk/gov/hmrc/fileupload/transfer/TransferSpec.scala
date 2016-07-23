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
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Second, Seconds, Span}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, Fixtures}
import uk.gov.hmrc.fileupload.Fixtures._
import uk.gov.hmrc.fileupload.transfer.Service.{EnvelopeAvailableEnvelopeNotFoundError, EnvelopeAvailableServiceError, TransferServiceError}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class TransferSpec extends UnitSpec with BeforeAndAfterAll with ScalaFutures with WithFakeApplication {

  private val port = 8080

  private val fileUploadBacked = new WireMockServer(wireMockConfig().port(port))

  private val baseUrl = s"http://localhost:$port"

  override def beforeAll() = {
    super.beforeAll()
    fileUploadBacked.start()
  }

  override def afterAll() = {
    super.afterAll()
    fileUploadBacked.stop()
  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(1, Second))

  "When calling the envelope check" should {

    val lookup = Service.envelopeAvailableCall(baseUrl) _

    "if the ID is known of return a success" in {
      val envelopeId = anyEnvelopeId

      respond(envelopeId, HTTP_OK)

      Service.envelopeAvailable(lookup)(envelopeId).futureValue shouldBe Xor.right(envelopeId)
    }

    "if the ID is not known of return an error" in {
      val envelopeId = anyEnvelopeId

      respond(envelopeId, HTTP_NOT_FOUND)

      Service.envelopeAvailable(lookup)(envelopeId).futureValue shouldBe Xor.left(EnvelopeAvailableEnvelopeNotFoundError(envelopeId))
    }

    "if an error occurs return an error" in {
      val envelopeId = anyEnvelopeId

      respond(envelopeId, HTTP_INTERNAL_ERROR, "SOME_ERROR")

      Service.envelopeAvailable(lookup)(envelopeId).futureValue shouldBe Xor.left(EnvelopeAvailableServiceError(envelopeId, "SOME_ERROR"))
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

  val transfer = Service.transfer(Service.transferCall(baseUrl)) _

  "When uploading a file" should {
    "be successful if file uploaded" in {
      val envelopeId = anyEnvelopeId
      val fileId = anyFileId

      respond(envelopeId, fileId, 200)

      transfer(Fixtures.anyFileFor(envelopeId, fileId)).futureValue shouldBe Xor.right(envelopeId)
    }

    "give an error if file uploaded" in {
      val envelopeId = anyEnvelopeId
      val fileId = anyFileId

      respond(envelopeId, fileId, 500, "SOME_ERROR")

      transfer(Fixtures.anyFileFor(envelopeId, fileId)).futureValue shouldBe Xor.left(TransferServiceError(envelopeId, "SOME_ERROR"))
    }

    def respond(envelopeId: EnvelopeId, fileId: FileId, status: Int, body: String = ""): Unit = {
      fileUploadBacked.addStubMapping(
        put(urlPathMatching(s"/file-upload/envelope/${envelopeId.value}/file/${fileId.value}/content"))
          .willReturn(new ResponseDefinitionBuilder()
            .withBody(body)
            .withStatus(status))
          .build())
    }
  }
}
