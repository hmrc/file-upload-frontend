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
  implicit val writes = new Writes[EnvelopeId] {
    def writes(id: EnvelopeId): JsValue = JsString(id.value)
  }
  implicit val reads = new Reads[EnvelopeId] {
    def reads(json: JsValue): JsResult[EnvelopeId] = json match {
      case JsString(value) => JsSuccess(EnvelopeId(value))
      case _ => JsError("invalid envelopeId")
    }
  }
  implicit val binder: PathBindable[EnvelopeId] =
    new SimpleObjectBinder[EnvelopeId](EnvelopeId.apply, _.value)
}

case class FileId(value: String = UUID.randomUUID().toString) extends AnyVal {
  override def toString: String = value
}

object FileId {
  implicit val writes = new Writes[FileId] {
    def writes(id: FileId): JsValue = JsString(id.value)
  }
  implicit val reads = new Reads[FileId] {
    def reads(json: JsValue): JsResult[FileId] = json match {
      case JsString(value) => JsSuccess(FileId(value))
      case _ => JsError("invalid fileId")
    }
  }
  // should reflect backend version
  implicit val urlBinder: PathBindable[FileId] =
    new SimpleObjectBinder[FileId](
      FileId.apply, // play already decodes the endpoint parameters
      fId => dynamicString(fId.value)
    )
}

case class File(data: InputStream, length: Long, filename: String, contentType: Option[String])

case class FileRefId(value: String = UUID.randomUUID().toString) extends AnyVal {
  override def toString: String = value
}
object FileRefId {
  implicit val writes = new Writes[FileRefId] {
    def writes(id: FileRefId): JsValue = JsString(id.value)
  }
  implicit val reads = new Reads[FileRefId] {
    def reads(json: JsValue): JsResult[FileRefId] = json match {
      case JsString(value) => JsSuccess(FileRefId(value))
      case _ => JsError("invalid fileId")
    }
  }
  implicit val binder: PathBindable[FileRefId] =
    new SimpleObjectBinder[FileRefId](FileRefId.apply, _.value)
}

trait Event {
  def envelopeId: EnvelopeId
  def fileId: FileId
  def fileRefId: FileRefId
}

package object fileupload {
  type ByteStream = Array[Byte]
}

class SimpleObjectBinder[T](bind: String => T, unbind: T => String)(implicit m: Manifest[T]) extends PathBindable[T] {
  override def bind(key: String, value: String): Either[String, T] = try {
    Right(bind(value))
  } catch {
    case _: Throwable => Left(s"Cannot parse parameter '$key' with value '$value' as '${m.runtimeClass.getSimpleName}'")
  }

  def unbind(key: String, value: T): String = unbind(value)
}

case class RequestId(value: String) extends AnyVal

case class HeaderCarrier(
  requestId: Option[RequestId]
) {
  def forwardedHeaders: Seq[(String, String)] =
    requestId.map("X-Request-ID" -> _.value).toSeq
}

object HeaderCarrier {
  def fromRequestHeader(rh: play.api.mvc.RequestHeader): HeaderCarrier =
    HeaderCarrier(
      requestId = rh.headers.get("X-Request-Id").map(RequestId)
    )

  def empty: HeaderCarrier =
    HeaderCarrier(
      requestId = None
    )
}
