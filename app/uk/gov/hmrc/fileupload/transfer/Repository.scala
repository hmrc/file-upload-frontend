/*
 * Copyright 2020 HM Revenue & Customs
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

import play.api.http.Status
import play.api.libs.json.{Json, JsValue}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.infrastructure.PlayHttp.PlayHttpError
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier

object Repository {

  val userAgent = "User-Agent" -> "FU-frontend-transfer"

  sealed trait EnvelopeDetailError
  object EnvelopeDetailError {
    case class EnvelopeDetailNotFoundError(id: EnvelopeId) extends EnvelopeDetailError
    case class EnvelopeDetailServiceError(id: EnvelopeId, message: String) extends EnvelopeDetailError
  }

  type EnvelopeDetailResult = Either[EnvelopeDetailError, JsValue]

  def envelopeDetail(
    auditedHttpCall : (WSRequest => Future[Either[PlayHttpError, WSResponse]]),
    baseUrl         : String,
    wsClient        : WSClient
  )(envelopeId      : EnvelopeId,
    hc              : HeaderCarrier
  )(implicit
    ec              : ExecutionContext
  ): Future[EnvelopeDetailResult] =
    auditedHttpCall(
      wsClient
        .url(s"$baseUrl/file-upload/envelopes/${envelopeId.value}")
        .withMethod("GET")
        .withHttpHeaders(userAgent +: hc.headers :_ *)
    ).map {
      case Left(error)     => Left(EnvelopeDetailError.EnvelopeDetailServiceError(envelopeId, error.message))
      case Right(response) => response.status match {
        case Status.OK        => Right(Json.parse(response.body))
        case Status.NOT_FOUND => Left(EnvelopeDetailError.EnvelopeDetailNotFoundError(envelopeId))
        case _                => Left(EnvelopeDetailError.EnvelopeDetailServiceError(envelopeId, response.body))
      }
    }
}
