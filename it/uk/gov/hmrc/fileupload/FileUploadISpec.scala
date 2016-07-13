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

import org.scalatest.BeforeAndAfterEach
import play.api.http.Status
import play.api.mvc.Result
import play.api.test.Helpers._
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DefaultDB
import reactivemongo.api.gridfs.GridFS
import reactivemongo.json.JSONSerializationPack
import uk.gov.hmrc.fileupload.connectors.{ClamAvScannerConnector, Clean, MongoQuarantineStoreConnector, Scanning}
import uk.gov.hmrc.fileupload.controllers.FileUploadController
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.scalatest.concurrent.Eventually._
import org.scalatest.time.SpanSugar._
import uk.gov.hmrc.fileupload.services.UploadService

class FileUploadISpec extends UnitSpec with WithFakeApplication with MongoSpecSupport with BeforeAndAfterEach {
  import reactivemongo.json.collection.JSONCollectionProducer
  import UploadFixtures._

  val testCollectionName: String = "testFileUploadCollection"
  val gfs = GridFS[JSONSerializationPack.type](mongo(), testCollectionName)

  lazy val mongoController = new FileUploadController with UploadService with TestFileUploadConnector
                                                      with MongoQuarantineStoreConnector with ClamAvScannerConnector
                                                      with TestMongoDbConnection with ServicesConfig {
    override val collectionName = testCollectionName
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
    val done = for {
      dropFiles <- gfs.files.drop()
      dropChunks <- gfs.chunks.drop()
    } yield true

    await(done) should be (true)
  }

  trait TestMongoDbConnection extends MongoDbConnection {
    override implicit val db: () => DefaultDB = mongoConnectorForTest.db
  }

  "upload - POST /upload with a MONGO backend" should {
    "result in a successResponse" in {
      val success = "http://some.success"
      val fakeRequest = createUploadRequest(successRedirectURL = Some(success))
      val result: Future[Result] = mongoController.upload().apply(fakeRequest)

      status(result) should be (Status.SEE_OTHER)
      redirectLocation(result) should be (Some(success))
    }

    "result in an entry being stored in mongo" in {
      val fakeRequest = createUploadRequest()
      val result: Future[Result] = mongoController.upload().apply(fakeRequest)

      status(result) should be (Status.SEE_OTHER)

      await(gfs.files.count()) should be (1)
    }

    "result in an file data being stored in mongo" in {
      val fakeRequest = createUploadRequest()
      val result: Future[Result] = mongoController.upload().apply(fakeRequest)

      status(result) should be (Status.SEE_OTHER)

      await(gfs.files.count()) should be (1)
      await(gfs.chunks.count()) should be (1)
    }

    "result in chunking in mongo" in {
      val fakeRequest = createUploadRequest(fileIds = Seq("767KBFile.txt"))
      val result: Future[Result] = mongoController.upload().apply(fakeRequest)

      status(result) should be (Status.SEE_OTHER)

      await(gfs.files.count()) should be (1)
      await(gfs.chunks.count()) should be (3)
    }

    "result in chunking in mongo - confirm boundary" in {
      val fakeRequest = createUploadRequest(fileIds = Seq("768KBFile.txt"))
      val result: Future[Result] = mongoController.upload().apply(fakeRequest)

      status(result) should be (Status.SEE_OTHER)

      await(gfs.files.count()) should be (1)
      await(gfs.chunks.count()) should be (4)
    }

    "result in an overwrite when the same file is uploaded multiple times" in {
      1 to 5 foreach { _ =>
        val success = "http://some.success"
        val fakeRequest = createUploadRequest(successRedirectURL = Some(success), fileIds = Seq("768KBFile.txt"))
        val result = mongoController.upload().apply(fakeRequest)
        status(result) should be (Status.SEE_OTHER)
        redirectLocation(result) should be (Some(success))
      }

      await(gfs.files.count()) should be (1)
      await(gfs.chunks.count()) should be (4)
    }

    "NOT result in an overwrite when the same filename is uploaded in differing envelopes" in {
      Seq("envelope1", "envelope2").foreach { eId =>
        val success = "http://some.success"
        val fakeRequest = createUploadRequest(successRedirectURL = Some(success), fileIds = Seq("768KBFile.txt"), envelopeId = Some(eId))
        val result = mongoController.upload().apply(fakeRequest)
        status(result) should be (Status.SEE_OTHER)
        redirectLocation(result) should be (Some(success))
      }

      await(gfs.files.count()) should be (2)
      await(gfs.chunks.count()) should be (8)
    }

    "result in the file progressing to 'Clean' state after upload completes" in {
      val success = "http://some.success"
      val fakeRequest = createUploadRequest(successRedirectURL = Some(success), fileIds = Seq("768KBFile.txt"))
      val result = await(mongoController.upload().apply(fakeRequest))

      eventually(timeout(5 seconds)) { await(mongoController.list(Scanning)); await(mongoController.list(Clean)).length should be (1) }
    }
  }
}
