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

package uk.gov.hmrc.fileupload.quarantine

import cats.data.Xor
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json.Json
import play.modules.reactivemongo.GridFSController._
import play.modules.reactivemongo.JSONFileToSave
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.gridfs.GridFS
import reactivemongo.api.{DB, DBMetaCommands}
import reactivemongo.bson.BSONDocument
import reactivemongo.json._
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.fileupload.JSONReadFile

case class FileData(length: Long = 0, data: Enumerator[Array[Byte]] = null)


import scala.concurrent.{ExecutionContext, Future}

object Repository {

  def apply(mongo: () => DB with DBMetaCommands): Repository = new Repository(mongo)

  type WriteFileResult = Xor[WriteFileError, EnvelopeId]

  sealed trait WriteFileError
  case class WriteFileNotPersistedError(id: EnvelopeId) extends WriteFileError
}

class Repository(mongo: () => DB with DBMetaCommands) {

  import reactivemongo.json.collection._

  lazy val gfs = GridFS[JSONSerializationPack.type](mongo(), "quarantine")

  private def metadata(envelopeId: EnvelopeId, fileId: FileId) =
    Json.obj("envelopeId" -> envelopeId.value, "fileId" -> fileId.value)

  def writeFile(filename: String, contentType: Option[String])(implicit ec: ExecutionContext): Iteratee[Array[Byte], Future[JSONReadFile]] = {
    gfs.iteratee(JSONFileToSave(filename = Some(filename), contentType = contentType))
  }

  def retrieveFile(id: String)(implicit ec: ExecutionContext): Future[Option[FileData]] = {
    gfs.find[BSONDocument, JSONReadFile](BSONDocument("_id" -> id)).headOption.map { file =>
      file.map( f => FileData(f.length, gfs.enumerate(f)))
    }
  }

  def removeAll()(implicit ec: ExecutionContext): Future[List[WriteResult]] = {
    val files = gfs.files.remove(Json.obj())
    val chunks = gfs.chunks.remove(Json.obj())
    Future.sequence(List(files, chunks))
  }
}
