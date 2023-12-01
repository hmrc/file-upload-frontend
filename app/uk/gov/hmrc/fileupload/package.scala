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

import java.util.UUID
import java.io.InputStream

import play.api.libs.json.{JsError, JsSuccess, _}
import play.api.mvc.PathBindable
import play.core.routing.dynamicString

case class EnvelopeId(value: String = UUID.randomUUID().toString) extends AnyVal {
  override def toString: String = value
}

object EnvelopeId {
  implicit val writes: Writes[EnvelopeId] =
    (id: EnvelopeId) => JsString(id.value)

  implicit val reads: Reads[EnvelopeId] =
    (json: JsValue) =>
      json match {
        case JsString(value) => JsSuccess(EnvelopeId(value))
        case _               => JsError("invalid envelopeId")
      }

  implicit val binder: PathBindable[EnvelopeId] =
    new SimpleObjectBinder[EnvelopeId](EnvelopeId.apply, _.value)
}

case class FileId(
  value: String = UUID.randomUUID().toString
) extends AnyVal {
  override def toString: String = value
}

object FileId {
  implicit val writes: Writes[FileId] =
    (id: FileId) => JsString(id.value)

  implicit val reads: Reads[FileId] =
    (json: JsValue) =>
      json match {
        case JsString(value) => JsSuccess(FileId(value))
        case _               => JsError("invalid fileId")
      }

  // should reflect backend version
  implicit val urlBinder: PathBindable[FileId] =
    new SimpleObjectBinder[FileId](
      FileId.apply, // play already decodes the endpoint parameters
      fId => dynamicString(fId.value)
    )
}

case class File(
  data       : InputStream,
  length     : Long,
  filename   : String,
  contentType: Option[String]
)

case class FileRefId(
  value: String = UUID.randomUUID().toString
) extends AnyVal {
  override def toString: String = value
}

object FileRefId {
  implicit val writes: Writes[FileRefId] =
    (id: FileRefId) => JsString(id.value)

  implicit val reads: Reads[FileRefId] =
    (json: JsValue) =>
      json match {
        case JsString(value) => JsSuccess(FileRefId(value))
        case _               => JsError("invalid fileId")
      }

  implicit val binder: PathBindable[FileRefId] =
    new SimpleObjectBinder[FileRefId](FileRefId.apply, _.value)
}

trait Event {
  def envelopeId: EnvelopeId
  def fileId    : FileId
  def fileRefId : FileRefId
}

package object fileupload {
  type ByteStream = Array[Byte]
}

class SimpleObjectBinder[T](bind: String => T, unbind: T => String)(implicit m: Manifest[T]) extends PathBindable[T] {
  override def bind(key: String, value: String): Either[String, T] =
    try {
      Right(bind(value))
    } catch {
      case _: Throwable => Left(s"Cannot parse parameter '$key' with value '$value' as '${m.runtimeClass.getSimpleName}'")
    }

  override def unbind(key: String, value: T): String =
    unbind(value)
}
