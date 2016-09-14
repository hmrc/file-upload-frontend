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

import java.util.UUID

import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json.{JsError, JsSuccess, _}
import play.api.mvc.PathBindable
import uk.gov.hmrc.play.binders.SimpleObjectBinder

import scala.concurrent.Future

case class EnvelopeId(value: String = UUID.randomUUID().toString) extends AnyVal {
  override def toString = value
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
  override def toString = value
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
  implicit val binder: PathBindable[FileId] =
    new SimpleObjectBinder[FileId](FileId.apply, _.value)
}

case class File(data: Enumerator[Array[Byte]], length: Long, filename: String, contentType: Option[String], envelopeId: EnvelopeId = EnvelopeId(null), fileId: FileId = FileId(null)) {
  def streamTo[A](iteratee: Iteratee[Array[Byte], A]): Future[A] = {
    data.run(iteratee)
  }
}

case class FileReferenceId(value: String = UUID.randomUUID().toString) extends AnyVal {
  override def toString = value
}
object FileReferenceId {
  implicit val writes = new Writes[FileReferenceId] {
    def writes(id: FileReferenceId): JsValue = JsString(id.value)
  }
  implicit val reads = new Reads[FileReferenceId] {
    def reads(json: JsValue): JsResult[FileReferenceId] = json match {
      case JsString(value) => JsSuccess(FileReferenceId(value))
      case _ => JsError("invalid fileId")
    }
  }
  implicit val binder: PathBindable[FileReferenceId] =
    new SimpleObjectBinder[FileReferenceId](FileReferenceId.apply, _.value)
}

