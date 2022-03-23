/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload

import java.net.HttpURLConnection._

import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSRequest}
import uk.gov.hmrc.fileupload.DomainFixtures._
import uk.gov.hmrc.fileupload.support.IntegrationTestApplicationComponents
import uk.gov.hmrc.fileupload.transfer.Repository
import uk.gov.hmrc.fileupload.transfer.Repository.EnvelopeDetailError
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

class RepositoryISpec extends IntegrationTestApplicationComponents {
  import ExecutionContext.Implicits.global

  val wsClient = app.injector.instanceOf[WSClient]

  "When calling the envelope check" should {

    val auditedHttpCall = (request: WSRequest, hc: HeaderCarrier) => request.execute().map(Right.apply)
    val envelopeDetail = Repository.envelopeDetail(auditedHttpCall, fileUploadBackendBaseUrl, wsClient) _

    "if the ID is known of return a success" in {
      val envelopeId = anyEnvelopeId
      val response = Json.parse(ENVELOPE_OPEN_RESPONSE)

      Wiremock.respondToEnvelopeCheck(envelopeId, HTTP_OK, body = response.toString)

      envelopeDetail(envelopeId, HeaderCarrier()).futureValue shouldBe Right(response)
    }

    "if the ID is not known of return an error" in {
      val envelopeId = anyEnvelopeId

      Wiremock.respondToEnvelopeCheck(envelopeId, HTTP_NOT_FOUND)

      envelopeDetail(envelopeId, HeaderCarrier()).futureValue shouldBe Left(EnvelopeDetailError.EnvelopeDetailNotFoundError(envelopeId))
    }

    "if an error occurs return an error" in {
      val envelopeId = anyEnvelopeId
      val errorBody = "SOME_ERROR"

      Wiremock.respondToEnvelopeCheck(envelopeId, HTTP_INTERNAL_ERROR, errorBody)

      envelopeDetail(envelopeId, HeaderCarrier()).futureValue shouldBe Left(EnvelopeDetailError.EnvelopeDetailServiceError(envelopeId, "SOME_ERROR"))
    }
  }
}
