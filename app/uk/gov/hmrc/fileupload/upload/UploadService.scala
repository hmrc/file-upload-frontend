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

package uk.gov.hmrc.fileupload.upload

import cats.data.Xor
import play.api.mvc.Request
import uk.gov.hmrc.fileupload.quarantine.QuarantineService._
import uk.gov.hmrc.fileupload.transfer.TransferService._
import uk.gov.hmrc.fileupload.{EnvelopeId, FileReferenceId}

import scala.concurrent.{ExecutionContext, Future}

object UploadService {

  type UploadResult = Xor[UploadError, EnvelopeId]

  sealed trait UploadError

  case class UploadServiceDownstreamError(id: EnvelopeId, message: String) extends UploadError
  case class UploadServiceEnvelopeNotFoundError(id: EnvelopeId) extends UploadError

  def upload(envelopeAvailable: EnvelopeId => Future[EnvelopeAvailableResult],
             transfer: (FileReferenceId, Request[_]) => Future[TransferResult],
             getFile: (FileReferenceId) => Future[QuarantineDownloadResult] )
            (fileReferenceId: FileReferenceId, request: Request[_])
            (implicit executionContext: ExecutionContext): Future[UploadResult] = {

    getFile(fileReferenceId) flatMap {
      case Xor.Right(file) =>
        val envelopeId = file.envelopeId

        for {
          envelopeAvailableResult <- envelopeAvailable(envelopeId)
          transferResult <- transfer(fileReferenceId, request)
        } yield {
          (for {
            _ <- envelopeAvailableResult
            _ <- transferResult
          } yield envelopeId).leftMap {
            case EnvelopeNotFoundError(_) => UploadServiceEnvelopeNotFoundError(envelopeId)
            case EnvelopeAvailableServiceError(_, message) => UploadServiceDownstreamError(envelopeId, message)
            case TransferServiceError(_, message) => UploadServiceDownstreamError(envelopeId, message)
          }
        }

      case Xor.Left(QuarantineDownloadFileNotFound) => Future.failed(throw new Exception("unexpected exception"))
    }
  }
}
