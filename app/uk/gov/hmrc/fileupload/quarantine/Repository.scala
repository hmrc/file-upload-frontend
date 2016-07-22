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
import play.api.libs.json.{JsUndefined, Json}
import play.modules.reactivemongo.JSONFileToSave
import reactivemongo.api.gridfs.GridFS
import reactivemongo.api.{DB, DBMetaCommands}
import reactivemongo.json.JSONSerializationPack
import uk.gov.hmrc.fileupload.{EnvelopeId, File, FileId}
import uk.gov.hmrc.fileupload.quarantine.Repository.{WriteFileNotPersistedError, WriteFileResult}

import scala.concurrent.{ExecutionContext, Future}

object Repository {

  def apply(mongo: () => DB with DBMetaCommands): Repository = new Repository(mongo)

  type WriteFileResult = Xor[WriteFileError, EnvelopeId]

  sealed trait WriteFileError
  case class WriteFileNotPersistedError(id: EnvelopeId) extends WriteFileError
}

class Repository(mongo: () => DB with DBMetaCommands) {

  import play.modules.reactivemongo.GridFSController.readFileReads
  import reactivemongo.json.ImplicitBSONHandlers._
  import reactivemongo.json.collection.JSONCollectionProducer

  lazy val gfs = GridFS[JSONSerializationPack.type](mongo(), "quarantine")

  private def metadata(envelopeId: EnvelopeId, fileId: FileId) =
    Json.obj("envelopeId" -> envelopeId.value, "fileId" -> fileId.value)

  def writeFile(file: File)(implicit ex: ExecutionContext): Future[WriteFileResult] = {
    val fileToSave = JSONFileToSave(filename = Some(file.filename), contentType = file.contentType, metadata = metadata(file.envelopeId, file.fileId))

    for {
      readFile <- file.data |>>> gfs.iteratee(fileToSave)
      result <- readFile map {
        case x if x.id.isInstanceOf[JsUndefined] => Xor.Left(WriteFileNotPersistedError(file.envelopeId))
        case _ => Xor.Right(file.envelopeId)
      }
    } yield result

  }

}
