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

package uk.gov.hmrc.fileupload.connectors

import play.api.Logger
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.{JsString, JsUndefined, JsValue, Json}
import play.modules.reactivemongo.{JSONFileToSave, MongoDbConnection}
import reactivemongo.api.ReadPreference
import reactivemongo.api.gridfs.{GridFS, ReadFile}
import reactivemongo.json.JSONSerializationPack
import uk.gov.hmrc.fileupload.Errors.FilePersisterError

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

sealed trait FileState

case object Unscanned extends FileState

case object Scanning extends FileState

case object Clean extends FileState

case object VirusDetected extends FileState

sealed case class FileData(data: Enumerator[Array[Byte]], name: String, contentType: String, envelopeId: String, fileId: String, status: FileState = Unscanned)

trait QuarantineStoreConnector {

  def list(status: FileState): Future[Seq[FileData]]

  def updateStatus(file: FileData, status: FileState): Future[FileData] = {
    val newFile = FileData(data = file.data, name = file.name, contentType = file.contentType, envelopeId = file.envelopeId, fileId = file.fileId, status = status)
    updateStatus(newFile) map { _ => newFile }
  }

  def updateStatus(file: FileData): Future[Unit]

  final def persistFile(file: FileData): Future[Try[String]] = {
    for {
      _ <- deleteFileBeforeWrite(file)
      write <- writeFile(file)
    } yield write
  }

  def deleteFileBeforeWrite(file: FileData): Future[Unit]

  def writeFile(file: FileData): Future[Try[String]]
}

trait MongoQuarantineStoreConnector extends QuarantineStoreConnector {
  self: MongoDbConnection =>

  import play.modules.reactivemongo.GridFSController.readFileReads
  import reactivemongo.json.ImplicitBSONHandlers._
  import reactivemongo.json.collection.JSONCollectionProducer

  val collectionName: String = "quarantine"

  lazy val gfs = GridFS[JSONSerializationPack.type](db(), collectionName)

  private def metadata(envelopeId: String, state: FileState = Unscanned) = Json.obj("envelopeId" -> envelopeId, "state" -> s"$state")

  private def byNameAndEnvelopeId(name: String, envelopeId: String) = Json.obj("filename" -> name, "metadata" -> metadata(envelopeId))

  private def byNameAndEnvelopeIdOnly(name: String, envelopeId: String) = Json.obj("filename" -> name, "$where" -> s"this.metadata.envelopeId == '$envelopeId'")

  private def byState(state: FileState) = Json.obj("$where" -> s"this.metadata.state == '$state'")

  private def setState(state: FileState) = Json.obj("$set" -> Json.obj("metadata.state" -> s"$state"))

  private def getTryEnvelopeId(file: FileData): PartialFunction[ReadFile[JSONSerializationPack.type, JsValue], Try[String]] = {
    case x if x.id.isInstanceOf[JsUndefined] => Failure(FilePersisterError(file.fileId, file.envelopeId))
    case _ => Success(file.envelopeId)
  }

  override def deleteFileBeforeWrite(file: FileData) = {
    gfs.files.find(byNameAndEnvelopeId(file.name, file.envelopeId)).one[JsValue].map {
      case Some(reference) => Some(reference \ "_id").map(gfs.remove(_))
      case _ => ()
    }
  }

  override def updateStatus(file: FileData): Future[Unit] = {
    gfs.files.update(byNameAndEnvelopeIdOnly(file.name, file.envelopeId), setState(file.status)) map { r =>
      Logger.info(s"Update status to ${file.status} complete: ${r.nModified == 1}")
    }
  }

  override def writeFile(file: FileData) = {
    val fileToSave = JSONFileToSave(filename = Option(file.name), contentType = Some(file.contentType),
      metadata = metadata(file.envelopeId, file.status))

    for {
      readFile <- file.data |>>> gfs.iteratee(fileToSave) map identity
      result <- readFile map getTryEnvelopeId(file)
    } yield result
  }

  override def list(state: FileState): Future[Seq[FileData]] = {
    val futureSeq = gfs.files.find(byState(state)).cursor[ReadFile[JSONSerializationPack.type, JsValue]](readPreference = ReadPreference.primary).collect[Seq]()

    futureSeq.map { readFiles =>
      readFiles.map { file =>
        val envelopeId = file.metadata.value.getOrElse("envelopeId", JsUndefined) match {
          case JsString(envId) => envId
          case _ => ""
        }

        FileData(data = gfs.enumerate(file), name = file.filename.getOrElse(""),
          envelopeId = envelopeId,
          contentType = file.contentType.getOrElse(""),
          fileId = file.id.as[String])
      }
    }
  }
}