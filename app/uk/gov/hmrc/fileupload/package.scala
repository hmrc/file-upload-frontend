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

package uk.gov.hmrc.fileupload

import play.api.libs.iteratee.Enumerator

case class EnvelopeId(value :String) extends AnyVal

case class FileId(value: String) extends AnyVal

case class File(data: Enumerator[Array[Byte]], filename: String, contentType: Option[String], envelopeId: EnvelopeId, fileId: FileId)

