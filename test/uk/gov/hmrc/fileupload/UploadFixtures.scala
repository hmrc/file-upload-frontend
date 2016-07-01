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

import java.io.{File, FileOutputStream, FilenameFilter}
import java.nio.file.Files._
import java.nio.file.Paths
import java.nio.file.StandardCopyOption._

import play.api.libs.Files
import play.api.libs.Files.TemporaryFile
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.mvc.MultipartFormData
import play.api.mvc.MultipartFormData.{BadPart, MissingFilePart}
import play.api.test.{FakeApplication, FakeHeaders, FakeRequest}
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DefaultDB
import uk.gov.hmrc.fileupload.Errors.EnvelopeValidationError
import uk.gov.hmrc.fileupload.connectors._
import uk.gov.hmrc.fileupload.controllers.FileUploadController
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet}
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.Future
import scala.io.Source
import scala.util.{Failure, Success, Try}

object UploadFixtures {
  import scala.concurrent.ExecutionContext.Implicits.global

  val tmpDir = System.getProperty("java.io.tmpdir")
  val validEnvelopeId = "1234567890"
  val validFileId = "0987654321"
  val fileController = new FileUploadController with TestFileUploadConnector with TmpFileQuarantineStoreConnector

  trait TestServicesConfig extends ServicesConfig {
    override def baseUrl(serviceName: String): String = null
  }

  trait TestFileUploadConnector extends FileUploadConnector with TestServicesConfig {

    override def validate(envelopeId: String)(implicit hc: HeaderCarrier): Future[Try[String]] = {
      Future.successful(envelopeId match {
        case "INVALID" => Failure(EnvelopeValidationError(envelopeId))
        case _ => Success(envelopeId)
      })
    }

    override val http: HttpGet = null
  }

  def toStringIteratee = Iteratee.fold[Array[Byte], String]("") { (s, d) => s ++ d.toString }

  def toFileIteratee(filename: String) = {
    val fos: FileOutputStream = new FileOutputStream(new File(filename))

    val it = Iteratee.fold[Array[Byte], FileOutputStream](fos) { (f, d) =>
      f.write(d)
      f
    }.map { fos =>
      fos.close()
    }

    it
  }

  trait TmpFileQuarantineStoreConnector extends QuarantineStoreConnector {
    def deleteFileBeforeWrite(file: FileData) = Future.successful(())

    override def writeFile(file: FileData) = {
      val fos: FileOutputStream = new FileOutputStream(new File(s"$tmpDir/${file.envelopeId}-${file.fileId}.Unscanned"))

      val it = toFileIteratee(s"$tmpDir/${file.envelopeId}-${file.fileId}.Unscanned").map[Try[String]] { _ =>
        Success(file.envelopeId)
      }

      file.data |>>> it
    }

    override def list(state: FileState): Future[Seq[FileData]] = {
      Future.successful {
        new File(s"$tmpDir").listFiles.filter(_.getName.endsWith(s".$state")).toList.map { f =>
          FileData(Enumerator.fromFile(f), f.getName, "n/a", f.getName.split("-").head, f.getName.split("-").tail.head)
        }
      }
    }
  }

  def file(name:String) = Try(Enumerator.fromFile(new File(s"test/resources/$name"))).getOrElse(Enumerator.empty)

  def filePart(name:String) = MultipartFormData.FilePart(name, name, Some("text/plain"), file(name))

  def createUploadRequest(successRedirectURL:Option[String] = Some("http://somewhere.com/success"),
                          failureRedirectURL:Option[String] = Some("http://somewhere.com/failure"),
                          envelopeId:Option[String] = Some(validEnvelopeId),
                          fileIds:Seq[String] = Seq("testUpload.txt"),
                          headers:Seq[(String, Seq[String])] = Seq()) = {
    var params = Map[String, Seq[String]]()

    def addParam(paramName: String)(value:String) = params = params + (paramName -> Seq(value))

    successRedirectURL.foreach(addParam("successRedirect"))
    failureRedirectURL.foreach(addParam("failureRedirect"))
    envelopeId.foreach(addParam("envelopeId"))

    val multipartBody = MultipartFormData[Enumerator[Array[Byte]]](params, fileIds.map(filePart), Seq[BadPart](), Seq[MissingFilePart]())

    FakeRequest(method = "POST", uri = "/upload", headers = FakeHeaders(headers), body = multipartBody)
  }
}
