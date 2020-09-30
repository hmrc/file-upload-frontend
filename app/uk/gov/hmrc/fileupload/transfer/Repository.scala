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
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.infrastructure.PlayHttp.PlayHttpError
import uk.gov.hmrc.fileupload.transfer.TransferService._
import scala.concurrent.{ExecutionContext, Future}

object Repository {

  val userAgent = "User-Agent" -> "FU-frontend-transfer"

  def envelopeAvailable(auditedHttpCall: (WSRequest => Future[Either[PlayHttpError, WSResponse]]), baseUrl: String, wSClient: WSClient)(envelopeId: EnvelopeId)
                       (implicit executionContext: ExecutionContext): Future[EnvelopeAvailableResult] =
    auditedHttpCall(wSClient
      .url(s"$baseUrl/file-upload/envelopes/${envelopeId.value}")
      .withMethod("GET")
      .withHttpHeaders(userAgent)
    ).map {
      case Left(error) => Left(EnvelopeAvailableServiceError(envelopeId, error.message))
      case Right(response) => response.status match {
        case Status.OK => Right(envelopeId)
        case Status.NOT_FOUND => Left(EnvelopeNotFoundError(envelopeId))
        case _ => Left(EnvelopeAvailableServiceError(envelopeId, response.body))
      }
    }

  def envelopeDetail(auditedHttpCall: (WSRequest => Future[Either[PlayHttpError, WSResponse]]),
                     baseUrl: String, wSClient: WSClient)
                    (envelopeId: EnvelopeId)
                    (implicit executionContext: ExecutionContext): Future[EnvelopeDetailResult] =
    auditedHttpCall(wSClient
      .url(s"$baseUrl/file-upload/envelopes/${envelopeId.value}")
      .withMethod("GET")
      .withHttpHeaders(userAgent)
    ).map {
      case Left(error) => Left(EnvelopeDetailServiceError(envelopeId, error.message))
      case Right(response) => response.status match {
        case Status.OK => Right(Json.parse(response.body))
        case Status.NOT_FOUND => Left(EnvelopeDetailNotFoundError(envelopeId))
        case _ => Left(EnvelopeDetailServiceError(envelopeId, response.body))
      }
    }
}
