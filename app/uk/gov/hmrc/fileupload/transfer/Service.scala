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
import play.api.http.Status
import play.api.libs.iteratee.Iteratee
import uk.gov.hmrc.fileupload.infrastructure.HttpStreamingBody
import uk.gov.hmrc.fileupload.{EnvelopeCallback, EnvelopeId, File}

import scala.concurrent.{ExecutionContext, Future}

object Service {

  type EnvelopeAvailableResult = Xor[EnvelopeAvailableError, EnvelopeId]

  sealed trait EnvelopeAvailableError
  case class EnvelopeNotFoundError(id: EnvelopeId) extends EnvelopeAvailableError
  case class EnvelopeAvailableServiceError(id: EnvelopeId, message: String) extends EnvelopeAvailableError

  def envelopeAvailable(isEnvelopeAvailable: (EnvelopeId) => Future[EnvelopeAvailableResult])(envelopeId: EnvelopeId)
                       (implicit executionContext: ExecutionContext): Future[EnvelopeAvailableResult] = {
    isEnvelopeAvailable(envelopeId)
  }

  type EnvelopeCallbackResult = Xor[CallbackAvailableError, EnvelopeCallback]

  sealed trait CallbackAvailableError
  case class CallbackNotFoundError(id: EnvelopeId) extends CallbackAvailableError
  case class CallbackRetrievalServiceError(id: EnvelopeId, message: String) extends CallbackAvailableError

  def envelopeCallback(findEvenlopeCallback: (EnvelopeId) => Future[EnvelopeCallbackResult] )(envelopeId: EnvelopeId)
                      (implicit executionContext: ExecutionContext): Future[EnvelopeCallbackResult] = {
    findEvenlopeCallback(envelopeId)
  }

  type TransferResult = Xor[TransferError, EnvelopeId]

  sealed trait TransferError
  case class TransferServiceError(id: EnvelopeId, message: String) extends TransferError

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