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
import uk.gov.hmrc.EnvelopeId
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

  case class TransferServiceError(id: EnvelopeId, message: String)

  def envelopeAvailable(check: EnvelopeId => Future[HttpResponse])(id: EnvelopeId)
                       (implicit executionContext: ExecutionContext): Future[EnvelopeAvailableResult] = {

    check(id).map(_ => Xor.right(id)).recover {
      case _: NotFoundException => Xor.left(EnvelopeAvailableEnvelopeNotFoundError(id))
      case throwable => Xor.left(EnvelopeAvailableServiceError(id, throwable.getMessage))
    }
  }

  def transfer(): Future[TransferResult] = ???

  def envelopeLookup(baseUrl: String, headerCarrier: HeaderCarrier)(envelopeId: EnvelopeId): Future[HttpResponse] = {
    implicit val hc = headerCarrier

    WSHttp.GET[HttpResponse](s"$baseUrl/file-upload/envelope/${envelopeId.value}")
  }
}
