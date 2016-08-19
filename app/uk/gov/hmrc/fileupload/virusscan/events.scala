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

package uk.gov.hmrc.fileupload.virusscan

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

case class NoVirusDetected(envelopeId: EnvelopeId, fileId: FileId)

object NoVirusDetected {
  implicit val noVirusDetectedFormats: Format[NoVirusDetected] = Json.format[NoVirusDetected]
}

case class VirusDetected(envelopeId: EnvelopeId, fileId: FileId, reason: String)

object VirusDetected {
  implicit val virusDetectedFormats: Format[VirusDetected] = Json.format[VirusDetected]
}
