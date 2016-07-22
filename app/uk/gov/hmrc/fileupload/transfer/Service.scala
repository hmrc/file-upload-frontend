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
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}
import uk.gov.hmrc.fileupload.WSHttp
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse, NotFoundException}

import scala.concurrent.{ExecutionContext, Future}

object Service {

  type EnvelopeAvailableResult = Xor[EnvelopeAvailableError, EnvelopeId]
  type TransferResult = Xor[TransferError, EnvelopeId]

  sealed trait EnvelopeAvailableError

  case class EnvelopeAvailableEnvelopeNotFoundError(id: EnvelopeId) extends EnvelopeAvailableError
  case class EnvelopeAvailableServiceError(id: EnvelopeId, message: String) extends EnvelopeAvailableError

  sealed trait TransferError

  case class TransferServiceError(id: EnvelopeId, message: String) extends TransferError

  def envelopeAvailable(httpCall: EnvelopeId => Future[HttpResponse])(envelopeId: EnvelopeId)
                       (implicit executionContext: ExecutionContext): Future[EnvelopeAvailableResult] = {

    httpCall(envelopeId).map(_ => Xor.right(envelopeId)).recover {
      case _: NotFoundException => Xor.left(EnvelopeAvailableEnvelopeNotFoundError(envelopeId))
      case throwable => Xor.left(EnvelopeAvailableServiceError(envelopeId, throwable.getMessage))
    }
  }

  def envelopeAvailableCall(baseUrl: String, headerCarrier: HeaderCarrier)(envelopeId: EnvelopeId): Future[HttpResponse] = {
    implicit val hc = headerCarrier

    WSHttp.GET[HttpResponse](s"$baseUrl/file-upload/envelope/${envelopeId.value}")
  }

  def transfer(httpCall: (EnvelopeId, FileId) => Future[HttpResponse])(envelopeId :EnvelopeId, fileId: FileId)
              (implicit executionContext: ExecutionContext): Future[TransferResult] = {
    httpCall(envelopeId, fileId).map(_ => Xor.right(envelopeId)).recover {
      case throwable => Xor.left(TransferServiceError(envelopeId, throwable.getMessage))
    }
  }

  def transferCall(baseUrl: String, headerCarrier: HeaderCarrier)(envelopeId :EnvelopeId, fileId: FileId): Future[HttpResponse] = {
    implicit val hc = headerCarrier

    WSHttp.PUT[String, HttpResponse](s"$baseUrl/file-upload/envelope/${envelopeId.value}/file/${fileId.value}/content", "")
  }
}
