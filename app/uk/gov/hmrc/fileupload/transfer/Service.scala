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
import uk.gov.hmrc.play.http.HttpResponse

import scala.concurrent.Future

object Service {

  type EnvelopeAvailableResult = Xor[EnvelopeAvailableError, String]
  type TransferResult = Xor[TransferError, String]

  sealed trait EnvelopeAvailableError
  case class EnvelopeAvailableEnvelopeNotFoundError(id: String)
  case class EnvelopeAvailableServiceError(id: String, message: String)

  sealed trait TransferError
  case class TransferServiceError(id: String, message: String)

  def envelopeAvailable(check: String => Future[HttpResponse])(id: String): Future[EnvelopeAvailableResult] = ???

  def transfer(): Future[TransferResult] = ???


}
