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
import play.api.mvc.Request
import uk.gov.hmrc.fileupload.infrastructure.HttpStreamingBody
import uk.gov.hmrc.fileupload.{FileId, EnvelopeId, File}

import scala.concurrent.{ExecutionContext, Future}

object TransferService {

  type EnvelopeAvailableResult = Xor[EnvelopeAvailableError, EnvelopeId]

  sealed trait EnvelopeAvailableError
  case class EnvelopeNotFoundError(id: EnvelopeId) extends EnvelopeAvailableError
  case class EnvelopeAvailableServiceError(id: EnvelopeId, message: String) extends EnvelopeAvailableError

  type TransferResult = Xor[TransferError, EnvelopeId]

  sealed trait TransferError
  case class TransferServiceError(id: EnvelopeId, message: String) extends TransferError

  def envelopeAvailable(isEnvelopeAvailable: (EnvelopeId) => Future[EnvelopeAvailableResult])(envelopeId: EnvelopeId)
                       (implicit executionContext: ExecutionContext): Future[EnvelopeAvailableResult] = {
    isEnvelopeAvailable(envelopeId)
  }

  def stream(baseUrl: String, publish: (AnyRef) => Unit,
             toHttpBodyStreamer: (String, EnvelopeId, FileId, Request[_]) => Iteratee[Array[Byte], HttpStreamingBody.Result])
            (file: File, request: Request[_])
            (implicit executionContext: ExecutionContext) = {

    val iterator = toHttpBodyStreamer(baseUrl, file.envelopeId, file.fileId, request)

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