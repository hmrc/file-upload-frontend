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
import play.api.libs.json.Json
import reactivemongo.api.gridfs.GridFS
import reactivemongo.api.{DB, DBMetaCommands}
import reactivemongo.json.JSONSerializationPack
import uk.gov.hmrc.fileupload.quarantine.Repository.WriteFileResult
import uk.gov.hmrc.fileupload.{EnvelopeId, File, FileId}

import scala.concurrent.{ExecutionContext, Future}

object Repository {

  def apply(mongo: () => DB with DBMetaCommands): Repository = new Repository(mongo)

  type WriteFileResult = Xor[WriteFileError, EnvelopeId]

  sealed trait WriteFileError
  case class WriteFileNotPersistedError(id: EnvelopeId) extends WriteFileError
}

class Repository(mongo: () => DB with DBMetaCommands) {

  import reactivemongo.json.collection.JSONCollectionProducer

  lazy val gfs = GridFS[JSONSerializationPack.type](mongo(), "quarantine")

  private def metadata(envelopeId: EnvelopeId, fileId: FileId) =
    Json.obj("envelopeId" -> envelopeId.value, "fileId" -> fileId.value)

  def writeFile(file: File)(implicit ex: ExecutionContext): Future[WriteFileResult] = {
    Future.successful(Xor.right(file.envelopeId))
  }
}
