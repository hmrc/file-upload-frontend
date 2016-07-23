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
import play.api.libs.iteratee.Iteratee
import play.api.libs.ws.{WS, WSResponse}
import uk.gov.hmrc.fileupload.{EnvelopeId, File}
import uk.gov.hmrc.play.http.{HeaderCarrier, NotFoundException}
import play.api.Play.current
import play.api.http.Status

import scala.concurrent.{ExecutionContext, Future}

object Service {

  type EnvelopeAvailableResult = Xor[EnvelopeAvailableError, EnvelopeId]
  type TransferResult = Xor[TransferError, EnvelopeId]

  sealed trait TransferError

  sealed trait EnvelopeAvailableError extends TransferError

  case class EnvelopeAvailableEnvelopeNotFoundError(id: EnvelopeId) extends EnvelopeAvailableError
  case class EnvelopeAvailableServiceError(id: EnvelopeId, message: String) extends EnvelopeAvailableError

  case class TransferServiceError(id: EnvelopeId, message: String) extends TransferError

  def envelopeAvailable(httpCall: EnvelopeId => Future[WSResponse])(envelopeId: EnvelopeId)
                       (implicit executionContext: ExecutionContext): Future[EnvelopeAvailableResult] = {

    httpCall(envelopeId).map {
      response => response.status match {
        case Status.OK => Xor.right(envelopeId)
        case Status.NOT_FOUND => Xor.left(EnvelopeAvailableEnvelopeNotFoundError(envelopeId))
        case _ => Xor.left(EnvelopeAvailableServiceError(envelopeId, response.body))
      }
    }
  }

  def envelopeAvailableCall(baseUrl: String)(envelopeId: EnvelopeId): Future[WSResponse] = {
    WS.url(s"$baseUrl/file-upload/envelope/${envelopeId.value}").get()
  }

  def transfer(httpCall: (File) => Future[WSResponse])(file: File)
              (implicit executionContext: ExecutionContext): Future[TransferResult] = {

    httpCall(file).map {
      response => response.status match {
        case Status.OK => Xor.right(file.envelopeId)
        case _ => Xor.left(TransferServiceError(file.envelopeId, response.body))
      }
    }
  }

  def transferCall(baseUrl: String)(file: File)(implicit ec: ExecutionContext): Future[WSResponse] = {
    Iteratee.flatten(file.data(Iteratee.consume[Array[Byte]]())).run.flatMap {
      data =>
        WS.url(s"$baseUrl/file-upload/envelope/${file.envelopeId.value}/file/${file.fileId.value}/content")
          .withHeaders("Content-Type" -> "application/octet-stream")
          .put(data)
    }
  }
}
