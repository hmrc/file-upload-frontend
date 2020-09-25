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

import cats.data.Xor
import play.api.http.Status
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json.JsValue
import play.api.mvc.Request
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.infrastructure.HttpStreamingBody
import uk.gov.hmrc.fileupload.quarantine.QuarantineService.{QuarantineDownloadFileNotFound, _}
import uk.gov.hmrc.fileupload.s3.S3KeyName
import scala.concurrent.{ExecutionContext, Future}

object TransferService {

  type EnvelopeAvailableResult = Xor[EnvelopeAvailableError, EnvelopeId]

  sealed trait EnvelopeAvailableError
  case class EnvelopeNotFoundError(id: EnvelopeId) extends EnvelopeAvailableError
  case class EnvelopeAvailableServiceError(id: EnvelopeId, message: String) extends EnvelopeAvailableError

  type TransferResult = Xor[TransferError, EnvelopeId]

  sealed trait TransferError
  case class TransferServiceError(id: EnvelopeId, message: String) extends TransferError

  type EnvelopeDetailResult = Xor[EnvelopeError, JsValue]
  sealed trait EnvelopeError
  case class EnvelopeDetailNotFoundError(id: EnvelopeId) extends EnvelopeError
  case class EnvelopeDetailServiceError(id: EnvelopeId, message: String) extends EnvelopeError

  def envelopeAvailable(isEnvelopeAvailable: (EnvelopeId) => Future[EnvelopeAvailableResult])(envelopeId: EnvelopeId)
                       (implicit executionContext: ExecutionContext): Future[EnvelopeAvailableResult] =
    isEnvelopeAvailable(envelopeId)

  def envelopeResult(result: (EnvelopeId) => Future[EnvelopeDetailResult])
                    (envelopeId: EnvelopeId)
                    (implicit executionContext: ExecutionContext): Future[EnvelopeDetailResult] =
    result(envelopeId)

  def stream(baseUrl: String,
             publish: (AnyRef) => Unit,
             toHttpBodyStreamer: (String, EnvelopeId, FileId, FileRefId, Request[_]) => Iteratee[Array[Byte], HttpStreamingBody.Result],
             getFile: (S3KeyName, String) => Future[QuarantineDownloadResult])
            (s3KeyAppender: (EnvelopeId, FileId) => S3KeyName)
            (envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId)
            (implicit executionContext: ExecutionContext): Future[TransferResult] = {
    val appendedKey = s3KeyAppender(envelopeId, fileId)
    getFile(appendedKey, fileRefId.value) flatMap {
      case Xor.Right(file) =>
        // TODO check request
        val iterator = toHttpBodyStreamer(baseUrl, envelopeId, fileId, fileRefId, null)

        (Enumerator.fromStream(file.data) |>>> iterator).map(r =>
          r.status match {
            case Status.OK =>
              Xor.Right(envelopeId)
            case _ =>
              Xor.Left(TransferServiceError(envelopeId, r.response))
          })

      case Xor.Left(QuarantineDownloadFileNotFound) =>
        Future.failed(throw new Exception(s"File not found in quarantine (fileRefId: $fileRefId, envelopeId: $envelopeId"))
    }

  }
}
