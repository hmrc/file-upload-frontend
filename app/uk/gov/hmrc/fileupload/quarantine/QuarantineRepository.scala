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

package uk.gov.hmrc.fileupload.quarantine

import java.io.InputStream

import org.joda.time.DateTime
import play.api.libs.json.{Format, Json, JsObject, JsString, OFormat, Reads, Writes}
import uk.gov.hmrc.fileupload.EnvelopeId

case class FileData(
  length     : Long,
  filename   : String,
  contentType: Option[String],
  data       : InputStream
)

//This class is needed to keep in sync with the backend
case class EnvelopeReport(id: Option[EnvelopeId] = None,
                          callbackUrl: Option[String] = None,
                          expiryDate: Option[DateTime] = None,
                          metadata: Option[JsObject] = None,
                          constraints: Option[EnvelopeConstraints] = None,
                          status: Option[String] = None,
                          destination: Option[String] = None,
                          application: Option[String] = None,
                          files: Option[Seq[JsObject]] = None)

object EnvelopeReport{
  implicit val envelopeFormat: OFormat[EnvelopeReport] = {
    implicit val dtr = Reads[DateTime](_.validate[String].map[DateTime](dtString => new DateTime(dtString)))
    implicit val dtw = Writes[DateTime](dt => JsString(dt.toString))
    Json.format[EnvelopeReport]
  }
}


case class EnvelopeConstraints(maxItems: Int,
                               maxSize: String,
                               maxSizePerItem: String,
                               allowZeroLengthFiles: Option[Boolean])

object EnvelopeConstraints {
  implicit val constraintsFormat: Format[EnvelopeConstraints] = Json.format[EnvelopeConstraints]
}
