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

import org.joda.time.{DateTime, Duration}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json.JsString
import reactivemongo.bson.BSONDocument
import reactivemongo.json.ImplicitBSONHandlers._
import uk.gov.hmrc.fileupload.FileRefId
import uk.gov.hmrc.fileupload.fileupload.{ByteStream, JSONReadFile}
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class RepositorySpec extends UnitSpec with MongoSpecSupport with WithFakeApplication with ScalaFutures with BeforeAndAfterEach {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(5, Millis))
  import scala.concurrent.ExecutionContext.Implicits.global

  val repository = new Repository(mongo)

  override def beforeEach {
    repository.clear(Duration.ZERO).futureValue
  }

  "repository" should {
    "provide an iteratee to store a stream" in {
      val text = "I only exists to be stored in mongo :<"
      val contents = Enumerator[ByteStream](text.getBytes)

      val filename = "myfile"
      val contentType = Some("application/octet-stream")

      val sink = repository.writeFile(filename, contentType)
      val fsId = contents.run[Future[JSONReadFile]](sink).futureValue.id match {
        case JsString(id) => id
        case _ => fail("expected JsString here")
      }

      val fileResult = repository.retrieveFile(FileRefId(fsId)).futureValue.get

      fileResult.length shouldBe text.getBytes.length
      val resultAsString = {
        val consume = Iteratee.consume[String]()
        fileResult.data.map(new String(_)).run(consume).futureValue
      }
      resultAsString shouldBe text
    }

    "returns a fileNotFound error" in {
      val nonexistentId = FileRefId("wrongid")

      val fileResult = repository.retrieveFile(nonexistentId).futureValue

      fileResult shouldBe None
    }

    "Clear files after expiry duration" in {
      val id = insertAnyFile()
      val imagineWeAre2DaysInTheFuture = () => DateTime.now().plusDays(3)
      val expiryDuration = Duration.standardDays(2)
      repository.clear(expiryDuration, imagineWeAre2DaysInTheFuture).futureValue

      val fileResult = repository.retrieveFile(FileRefId(id)).futureValue

      repository.gfs.files.find(BSONDocument("_id" -> id)).one[BSONDocument].futureValue.isDefined shouldBe true
      repository.gfs.chunks.find(BSONDocument("files_id" -> id)).one[BSONDocument].futureValue.isDefined shouldBe true
    }

    "Do not clear files within expiry duration" in {
      val id = insertAnyFile()
      val imagineWeAre2DaysInTheFuture = () => DateTime.now().plusDays(3)
      val expiryDuration = Duration.standardDays(4)
      repository.clear(expiryDuration, imagineWeAre2DaysInTheFuture).futureValue

      val fileResult = repository.retrieveFile(FileRefId(id)).futureValue

      repository.gfs.files.find(BSONDocument("_id" -> id)).one[BSONDocument].futureValue shouldBe None
      repository.gfs.chunks.find(BSONDocument("files_id" -> id)).one[BSONDocument].futureValue shouldBe None
    }
  }

  def insertAnyFile(): String = {
    val sink = repository.writeFile("fileName", None)
    Enumerator[ByteStream]("testFile".getBytes).run[Future[JSONReadFile]](sink).futureValue.id match {
      case JsString(id) => id
      case _ => fail("expected JsString here")
    }
  }
}
