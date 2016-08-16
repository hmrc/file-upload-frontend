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
import play.api.libs.iteratee.Iteratee
import play.api.libs.ws.{WS, WSRequestHolder, WSResponse}
import uk.gov.hmrc.fileupload.infrastructure.HttpStreamingBody
import uk.gov.hmrc.fileupload.infrastructure.PlayHttp.PlayHttpError
import uk.gov.hmrc.fileupload.{EnvelopeId, File}

import scala.concurrent.{ExecutionContext, Future}

object Service {

  type EnvelopeAvailableResult = Xor[EnvelopeAvailableError, EnvelopeId]
  type TransferResult = Xor[TransferError, EnvelopeId]

  sealed trait TransferError

  sealed trait EnvelopeAvailableError extends TransferError

  case class EnvelopeNotFoundError(id: EnvelopeId) extends EnvelopeAvailableError

  case class EnvelopeAvailableServiceError(id: EnvelopeId, message: String) extends EnvelopeAvailableError

  case class TransferServiceError(id: EnvelopeId, message: String) extends TransferError

  def envelopeAvailable(httpCall: (WSRequestHolder => Future[Xor[PlayHttpError, WSResponse]]), baseUrl: String)(envelopeId: EnvelopeId)
                       (implicit executionContext: ExecutionContext): Future[EnvelopeAvailableResult] = {

    httpCall(WS.url(s"$baseUrl/file-upload/envelope/${envelopeId.value}").withMethod("GET")).map {
      case Xor.Left(error) => Xor.left(EnvelopeAvailableServiceError(envelopeId, error.message))
      case Xor.Right(response) => response.status match {
        case Status.OK => Xor.right(envelopeId)
        case Status.NOT_FOUND => Xor.left(EnvelopeNotFoundError(envelopeId))
        case _ => Xor.left(EnvelopeAvailableServiceError(envelopeId, response.body))
      }
    }
  }

//  def transfer(httpCall: (WSRequestHolder => Future[Xor[PlayHttpError, WSResponse]]), baseUrl: String)(file: File)
//              (implicit executionContext: ExecutionContext): Future[TransferResult] = {
//
//    httpCall(WS.url(s"$baseUrl/file-upload/envelope/${file.envelopeId.value}/file/${file.fileId.value}/content")
//      .withHeaders("Content-Type" -> "application/octet-stream")
//      .withBody(file.data)
//      .withMethod("PUT"))
//      .map {
//        case Xor.Left(error) => Xor.left(TransferServiceError(file.envelopeId, error.message))
//        case Xor.Right(response) => response.status match {
//          case Status.OK => Xor.right(file.envelopeId)
//          case _ => Xor.left(TransferServiceError(file.envelopeId, response.body))
//        }
//      }
//  }

  def stream(baseUrl: String, publish: (AnyRef) => Unit)(file: File)
            (implicit executionContext: ExecutionContext) = {
    val iterator: Iteratee[Array[Byte], HttpStreamingBody.Result] = HttpStreamingBody(
      url = s"$baseUrl/file-upload/envelope/${ file.envelopeId.value }/file/${ file.fileId.value }/content",
      method = "PUT")

    (file.data |>>> iterator).map( r =>
      r.status match {
        case Status.OK =>
          publish(ToTransientMoved(file.envelopeId, file.fileId))
          Xor.Right(file.envelopeId)
        case _ =>
          publish(MovingToTransientFailed(file.envelopeId, file.fileId, r.response))
          Xor.Left(TransferServiceError(file.envelopeId, r.response))
      })
  }
}