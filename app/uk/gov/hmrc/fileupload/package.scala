/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.libs.json.{JsError, JsSuccess, _}
import play.api.mvc.PathBindable
import play.core.routing.dynamicString

import java.util.UUID
import java.io.InputStream
import scala.reflect.ClassTag

case class EnvelopeId(value: String = UUID.randomUUID().toString) extends AnyVal {
  override def toString: String = value
}

object EnvelopeId:
  given Writes[EnvelopeId] =
    (id: EnvelopeId) => JsString(id.value)

  given Reads[EnvelopeId] =
    (json: JsValue) =>
      json match {
        case JsString(value) => JsSuccess(EnvelopeId(value))
        case _               => JsError("invalid envelopeId")
      }

  given PathBindable[EnvelopeId] =
    SimpleObjectBinder[EnvelopeId](EnvelopeId.apply, _.value)

end EnvelopeId

case class FileId(
  value: String = UUID.randomUUID().toString
) extends AnyVal:
  override def toString: String = value

object FileId:
  given Writes[FileId] =
    (id: FileId) => JsString(id.value)

  given Reads[FileId] =
    (json: JsValue) =>
      json match {
        case JsString(value) => JsSuccess(FileId(value))
        case _               => JsError("invalid fileId")
      }

  // should reflect backend version
  given PathBindable[FileId] =
    SimpleObjectBinder[FileId](
      FileId.apply, // play already decodes the endpoint parameters
      fId => dynamicString(fId.value)
    )

end FileId

case class File(
  data       : InputStream,
  length     : Long,
  filename   : String,
  contentType: Option[String]
)

case class FileRefId(
  value: String = UUID.randomUUID().toString
) extends AnyVal:
  override def toString: String = value

object FileRefId {
  given Writes[FileRefId] =
    (id: FileRefId) => JsString(id.value)

  given Reads[FileRefId] =
    (json: JsValue) =>
      json match
        case JsString(value) => JsSuccess(FileRefId(value))
        case _               => JsError("invalid fileId")

  given PathBindable[FileRefId] =
    SimpleObjectBinder[FileRefId](FileRefId.apply, _.value)
}

trait Event {
  def envelopeId: EnvelopeId
  def fileId    : FileId
  def fileRefId : FileRefId
}

class SimpleObjectBinder[T](bind: String => T, unbind: T => String)(using ct: ClassTag[T]) extends PathBindable[T]:
  override def bind(key: String, value: String): Either[String, T] =
    try {
      Right(bind(value))
    } catch {
      case _: Throwable => Left(s"Cannot parse parameter '$key' with value '$value' as '${ct.runtimeClass.getSimpleName}'")
    }

  override def unbind(key: String, value: T): String =
    unbind(value)
