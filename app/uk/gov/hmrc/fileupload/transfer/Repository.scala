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

import cats.data.Xor
import play.api.Play.current
import play.api.http.Status
import play.api.libs.json.JsObject
import play.api.libs.ws.{WS, WSRequestHolder, WSResponse}
import uk.gov.hmrc.fileupload.infrastructure.PlayHttp.PlayHttpError
import uk.gov.hmrc.fileupload.transfer.TransferService._
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

import scala.concurrent.{ExecutionContext, Future}

object Repository {

  def envelopeAvailable(auditedHttpCall: (WSRequestHolder => Future[Xor[PlayHttpError, WSResponse]]), baseUrl: String)(envelopeId: EnvelopeId)
                       (implicit executionContext: ExecutionContext): Future[EnvelopeAvailableResult] = {

    auditedHttpCall(WS.url(s"$baseUrl/file-upload/envelope/${ envelopeId.value }").withMethod("GET")).map {
      case Xor.Left(error) => Xor.left(EnvelopeAvailableServiceError(envelopeId, error.message))
      case Xor.Right(response) => response.status match {
        case Status.OK => Xor.right(envelopeId)
        case Status.NOT_FOUND => Xor.left(EnvelopeNotFoundError(envelopeId))
        case _ => Xor.left(EnvelopeAvailableServiceError(envelopeId, response.body))
      }
    }
  }

  type SendMetadataResult = Xor[SendMetadataError, SendMetadataSuccess.type]
  case object SendMetadataSuccess
  sealed trait SendMetadataError
  case class SendMetadataOtherError(message: String) extends SendMetadataError
  case class SendMetadataEnvelopeNotFound(envelopeId: EnvelopeId) extends SendMetadataError

  def sendMetadata(auditedHttpCall: WSRequestHolder => Future[Xor[PlayHttpError, WSResponse]], baseUrl: String)
                  (envelopeId: EnvelopeId, fileId: FileId, metadata: JsObject)
                  (implicit ec: ExecutionContext) = {

    auditedHttpCall(
      WS.url(s"$baseUrl/file-upload/envelope/$envelopeId/file/$fileId/metadata")
      .withMethod("PUT")
      .withBody(metadata)
    ).map {
      case Xor.Left(error) => Xor.left(SendMetadataOtherError(error.message))
      case Xor.Right(response) => response.status match {
        case Status.OK => Xor.right(SendMetadataSuccess)
        case Status.NOT_FOUND => Xor.left(SendMetadataEnvelopeNotFound(envelopeId))
        case _ => Xor.left(SendMetadataOtherError(s"Status: ${response.status}, body: ${response.body}"))
      }
    }
  }

}