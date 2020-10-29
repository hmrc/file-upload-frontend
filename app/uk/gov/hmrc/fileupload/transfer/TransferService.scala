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

import play.api.libs.json.JsValue
import uk.gov.hmrc.fileupload.EnvelopeId

object TransferService {

  type EnvelopeAvailableResult = Either[EnvelopeAvailableError, EnvelopeId]

  sealed trait EnvelopeAvailableError
  case class EnvelopeNotFoundError(id: EnvelopeId) extends EnvelopeAvailableError
  case class EnvelopeAvailableServiceError(id: EnvelopeId, message: String) extends EnvelopeAvailableError

  type TransferResult = Either[TransferError, EnvelopeId]

  sealed trait TransferError
  case class TransferServiceError(id: EnvelopeId, message: String) extends TransferError

  type EnvelopeDetailResult = Either[EnvelopeError, JsValue]
  sealed trait EnvelopeError
  case class EnvelopeDetailNotFoundError(id: EnvelopeId) extends EnvelopeError
  case class EnvelopeDetailServiceError(id: EnvelopeId, message: String) extends EnvelopeError
}
