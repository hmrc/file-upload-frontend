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

import java.io.File

import org.scalatest.BeforeAndAfterEach
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.{JsValue, Json}
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DefaultDB
import reactivemongo.api.gridfs.GridFS
import reactivemongo.json.JSONSerializationPack
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.io.Source._

class QuarantineStoreConnectorSpec extends UnitSpec with WithFakeApplication with MongoSpecSupport with BeforeAndAfterEach {
  import uk.gov.hmrc.fileupload.UploadFixtures._
  import reactivemongo.json.collection.JSONCollectionProducer
  import reactivemongo.json.ImplicitBSONHandlers._
  import scala.concurrent.ExecutionContext.Implicits.global

  def testFile = Enumerator.fromFile(new File("test/resources/testUpload.txt"))
  val testCollectionName: String = "testFileUploadCollection"
  val gfs = GridFS[JSONSerializationPack.type](mongo(), testCollectionName)

  lazy val fileSystemConnector = new TmpFileQuarantineStoreConnector {}

  lazy val mongoConnector = new MongoQuarantineStoreConnector with TestMongoDbConnection {
    override val collectionName = testCollectionName
  }

  trait TestMongoDbConnection extends MongoDbConnection {
    override implicit val db: () => DefaultDB = mongoConnectorForTest.db
  }

  "A quarantine store" should {
    "Have a mechanism that can successfully persist a file" in {
      val envId = "12345"
      val data = FileData(data = testFile, name = "TEST.out", contentType = "text/plain", envelopeId = envId, fileId = "1")

      await(fileSystemConnector.persistFile(data))

      new File(s"$tmpDir/$envId-1.Unscanned") should be a 'file
    }

    "Have a mechanism that can successfully persist to a mongo instance" in {
      val envId = "12345"
      val data = FileData(data = testFile, name = "TEST.out", contentType = "text/plain", envelopeId = envId, fileId = "1")

      await(mongoConnector.persistFile(data))

      await(gfs.files.find(Json.obj("filename" -> "TEST.out", "$where" -> s"this.metadata.envelopeId == '$envId'")).one[JsValue]) should not be empty
      await(gfs.files.find(Json.obj("filename" -> "TEST.out", "$where" -> s"this.metadata.envelopeId == 'x$envId'")).one[JsValue]) should be (empty)
    }

    "Have a mechanism that can successfully persist same files with different envelopes" in {
      1 to 2 foreach { id =>
        val data = FileData(data = testFile, name = "TEST.out", contentType = "text/plain", envelopeId = s"envelope$id", fileId = "1")
        await(fileSystemConnector.persistFile(data))
        new File(s"$tmpDir/envelope$id-1.Unscanned") should be a 'file
      }
    }

    "Have a mechanism that can successfully persist same files with different envelopes to Mongo" in {
      1 to 2 foreach { id =>
        val data = FileData(data = testFile, name = "TEST.out", contentType = "text/plain", envelopeId = s"envelope$id", fileId = "1")
        await(mongoConnector.persistFile(data))
        await(gfs.files.find(Json.obj("filename" -> "TEST.out", "$where" -> s"this.metadata.envelopeId == 'envelope$id'")).one[JsValue]) should not be empty
        await(gfs.files.find(Json.obj("filename" -> "TEST2.out", "$where" -> s"this.metadata.envelopeId == 'envelope$id'")).one[JsValue]) should be (empty)
      }
    }

    "Have a mechanism to list files based on their 'state' being 'unscanned'" in {
      1 to 2 foreach { id =>
        val data = FileData(data = testFile, name = "TEST.out", contentType = "text/plain", envelopeId = s"envelope$id", fileId = "1")
        await(fileSystemConnector.persistFile(data))
      }

      await(fileSystemConnector.list(Unscanned)).length should be (2)
    }

    "Have a mechanism to list files based on their 'state' being 'unscanned' in Mongo" in {
      1 to 2 foreach { id =>
        val data = FileData(data = testFile, name = "TEST.out", contentType = "text/plain", envelopeId = s"envelope$id", fileId = "1")
        await(mongoConnector.persistFile(data))
      }

      await(mongoConnector.list(Unscanned)).length should be (2)
    }

    "Have a mechanism to successfully retrieve a persisted file from the list" in {
      1 to 10 foreach { id =>
        val envId = s"12345$id"
        val fileId = s"$id"
        val data = FileData(data = testFile, name = "TEST.out", contentType = "text/plain", envelopeId = envId, fileId = fileId)

        await(fileSystemConnector.persistFile(data))
      }

      val fileSeq = await(fileSystemConnector.list(Unscanned))

      fileSeq.length should be (10)

      fileSeq.foreach { fileData =>
        await(fileData.data |>>> toStringIteratee) === fromFile(new File("test/resources/testUpload.txt")).mkString
      }
    }

    "Have a mechanism to successfully retrieve a persisted file from the list in mongo" in {
      1 to 10 foreach { id =>
        val envId = s"12345$id"
        val fileId = s"$id"
        val data = FileData(data = testFile, name = "TEST.out", contentType = "text/plain", envelopeId = envId, fileId = fileId)

        await(mongoConnector.persistFile(data))
      }

      val fileSeq = await(mongoConnector.list(Unscanned))

      fileSeq.length should be (10)

      fileSeq.foreach { fileData =>
        await(fileData.data |>>> toStringIteratee) === fromFile(new File("test/resources/testUpload.txt")).mkString
      }
    }
  }

  override protected def beforeEach() = {
    val isClean = for {
      dropFiles <- gfs.files.drop()
      dropChunks <- gfs.chunks.drop()
      fileCount <- gfs.files.count()
      chunkCount <- gfs.chunks.count()
    } yield fileCount + chunkCount == 0

    await(isClean) should be (true)
  }

  override protected def afterEach() = {
    new File(s"$tmpDir").listFiles().filter(_.getName.endsWith(".Unscanned")).foreach(_.delete())
    val done = for {
      dropFiles <- gfs.files.drop()
      dropChunks <- gfs.chunks.drop()
    } yield true

    await(done) should be (true)
  }
}
