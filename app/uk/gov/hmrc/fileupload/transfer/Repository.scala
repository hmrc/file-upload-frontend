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
import uk.gov.hmrc.fileupload.{EnvelopeId, RequestId}
import uk.gov.hmrc.fileupload.infrastructure.PlayHttp.PlayHttpError
import scala.concurrent.{ExecutionContext, Future}

object Repository {

  val userAgent = "User-Agent" -> "FU-frontend-transfer"

  sealed trait EnvelopeAvailableError
  object EnvelopeAvailableError {
    case class EnvelopeNotFoundError(id: EnvelopeId) extends EnvelopeAvailableError
    case class EnvelopeAvailableServiceError(id: EnvelopeId, message: String) extends EnvelopeAvailableError
  }

  def envelopeAvailable(
    auditedHttpCall : (WSRequest => Future[Either[PlayHttpError, WSResponse]]),
    baseUrl         : String,
    wSClient        : WSClient
  )(envelopeId      : EnvelopeId,
    requestId       : Option[RequestId]
  )(implicit
    executionContext: ExecutionContext
  ): Future[Either[EnvelopeAvailableError, EnvelopeId]] =
    auditedHttpCall(wSClient
      .url(s"$baseUrl/file-upload/envelopes/${envelopeId.value}")
      .withMethod("GET")
      .withHttpHeaders(userAgent +: requestId.map("X-Request-ID" -> _.value).toSeq :_ *)
    ).map {
      case Left(error)     => Left(EnvelopeAvailableError.EnvelopeAvailableServiceError(envelopeId, error.message))
      case Right(response) => response.status match {
        case Status.OK        => Right(envelopeId)
        case Status.NOT_FOUND => Left(EnvelopeAvailableError.EnvelopeNotFoundError(envelopeId))
        case _                => Left(EnvelopeAvailableError.EnvelopeAvailableServiceError(envelopeId, response.body))
      }
    }

  sealed trait EnvelopeDetailError
  object EnvelopeDetailError {
    case class EnvelopeDetailNotFoundError(id: EnvelopeId) extends EnvelopeDetailError
    case class EnvelopeDetailServiceError(id: EnvelopeId, message: String) extends EnvelopeDetailError
  }

  def envelopeDetail(
    auditedHttpCall : (WSRequest => Future[Either[PlayHttpError, WSResponse]]),
    baseUrl         : String,
    wSClient        : WSClient
  )(envelopeId      : EnvelopeId,
    requestId       : Option[RequestId]
  )(implicit
    executionContext: ExecutionContext
  ): Future[Either[EnvelopeDetailError, JsValue]] =
    auditedHttpCall(wSClient
      .url(s"$baseUrl/file-upload/envelopes/${envelopeId.value}")
      .withMethod("GET")
      .withHttpHeaders(userAgent +: requestId.map("X-Request-ID" -> _.value).toSeq :_ *)
    ).map {
      case Left(error)     => Left(EnvelopeDetailError.EnvelopeDetailServiceError(envelopeId, error.message))
      case Right(response) => response.status match {
        case Status.OK        => Right(Json.parse(response.body))
        case Status.NOT_FOUND => Left(EnvelopeDetailError.EnvelopeDetailNotFoundError(envelopeId))
        case _                => Left(EnvelopeDetailError.EnvelopeDetailServiceError(envelopeId, response.body))
      }
    }
}
