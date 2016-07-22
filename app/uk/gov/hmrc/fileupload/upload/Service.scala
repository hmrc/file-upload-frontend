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
import uk.gov.hmrc.EnvelopeId
import uk.gov.hmrc.fileupload.quarantine.File
import uk.gov.hmrc.fileupload.quarantine.Service.QuarantineUploadResult
import uk.gov.hmrc.fileupload.transfer.Service.{EnvelopeAvailableEnvelopeNotFoundError, EnvelopeAvailableResult, EnvelopeAvailableServiceError, TransferResult}
import uk.gov.hmrc.fileupload.virusscan.Service.ScanResult

import scala.concurrent.{ExecutionContext, Future}

object Service {

  type UploadResult = Xor[UploadError, EnvelopeId]

  sealed trait UploadError
  case class UploadServiceError(id: EnvelopeId, message: String) extends UploadError
  case class UploadRequestError(id: EnvelopeId, message: String) extends UploadError

  def upload(envelopeAvailable: EnvelopeId => Future[EnvelopeAvailableResult],
             transfer: _ => Future[TransferResult],
             quarantine: File => QuarantineUploadResult,
             scan: _ => ScanResult)(envelopeId: EnvelopeId)(implicit executionContext: ExecutionContext): Future[UploadResult] = {

    envelopeAvailable(envelopeId) map {
      case Xor.Right(_) => Xor.right(envelopeId)
      case Xor.Left(EnvelopeAvailableEnvelopeNotFoundError(_)) => Xor.left(UploadServiceError(envelopeId, s"Envelope ID [${envelopeId.value}] does not exist"))
      case Xor.Left(EnvelopeAvailableServiceError(_, message)) => Xor.left(UploadServiceError(envelopeId, message))
    }
  }
}
