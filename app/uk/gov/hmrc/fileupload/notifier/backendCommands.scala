/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.notifier

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}

sealed trait BackendCommand {
  def commandType: String
  def id: EnvelopeId
  def fileId: FileId
  def fileRefId: FileRefId
}

case class QuarantineFile(id: EnvelopeId, fileId: FileId, fileRefId: FileRefId,
                          created: Long, name: String, contentType: String, length: Long, metadata: JsObject) extends BackendCommand {
  val commandType = "quarantine-file"
}

object QuarantineFile {
  implicit val format = Json.format[QuarantineFile]
}

case class MarkFileAsClean(id: EnvelopeId, fileId: FileId, fileRefId: FileRefId) extends BackendCommand {
  val commandType = "mark-file-as-clean"
}

object MarkFileAsClean {
  implicit val format = Json.format[MarkFileAsClean]
}

case class MarkFileAsInfected(id: EnvelopeId, fileId: FileId, fileRefId: FileRefId) extends BackendCommand {
  val commandType = "mark-file-as-infected"
}

object MarkFileAsInfected {
  implicit val format = Json.format[MarkFileAsInfected]
}

case class StoreFile(id: EnvelopeId, fileId: FileId, fileRefId: FileRefId, length: Long) extends BackendCommand {
  val commandType = "store-file"
}

object StoreFile {
  implicit val format = Json.format[StoreFile]
}
