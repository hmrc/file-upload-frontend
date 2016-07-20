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

package uk.gov.hmrc.fileupload_deprecated

import java.io.{File, FileOutputStream}

import org.scalatest.time.Span
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.mvc.MultipartFormData
import play.api.mvc.MultipartFormData.{BadPart, MissingFilePart}
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.clamav.VirusChecker
import uk.gov.hmrc.fileupload_deprecated.Errors.EnvelopeValidationError
import uk.gov.hmrc.fileupload_deprecated.connectors._
import uk.gov.hmrc.fileupload_deprecated.controllers.FileUploadController
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import org.scalatest.time.SpanSugar._
import uk.gov.hmrc.fileupload_deprecated.services.UploadService

object UploadFixtures {
  import scala.concurrent.ExecutionContext.Implicits.global

  val tmpDir = System.getProperty("java.io.tmpdir")
  val validEnvelopeId = "fea8cc15-f2d1-4eb5-b10e-b892bcbe94f8"
  val validFileId = "0987654321"
  val fileController = testFileUploadController()

  def testFileUploadController(avScannerConnector: AvScannerConnector = new StubResonseAvScannerConnector()): FileUploadController = {
    new FileUploadController(new UploadService(avScannerConnector, TestFileUploadConnector, TmpFileQuarantineStoreConnector))
  }

  val testCollectionName: String = "testFileUploadCollection"
  val eicarFile = "eicar-standard-av-test-file.txt"

  class StubResonseAvScannerConnector(response: Try[Boolean] = Success(true)) extends AvScannerConnector {

    override def scan(enumerator: Enumerator[Array[Byte]]): Future[Try[Boolean]] = Future.successful(response)
  }

  class StubCheckingVirusChecker extends VirusChecker {
    var sentData: Array[Byte] = Array()
    var scanInitiated = false
    var scanCompleted = false

    override def send(bytes: Array[Byte])(implicit ec: ExecutionContext) = {
      Future {
        scanInitiated = true
        sentData = sentData ++ bytes
      }
    }

    override def finish()(implicit ec : ExecutionContext) = {
      Future {
        Success(true)
      }.map { r =>
        scanCompleted = true
        r
      }
    }
  }

  trait TestServicesConfig extends ServicesConfig {
    override def baseUrl(serviceName: String): String = null
  }

  object TestFileUploadConnector extends FileUploadConnector with TestServicesConfig {

    override def validate(envelopeId: String)(implicit hc: HeaderCarrier): Future[Try[String]] = {
      Future.successful(envelopeId match {
        case "INVALID" => Failure(EnvelopeValidationError(envelopeId))
        case _ => Success(envelopeId)
      })
    }

    override val http: HttpGet = null
  }

  object TmpFileQuarantineStoreConnector extends QuarantineStoreConnector {
    def deleteFileBeforeWrite(file: FileData) = Future.successful(())

    override def writeFile(file: FileData) = {
      val it = toFileIteratee(s"$tmpDir/${file.envelopeId}-${file.fileId}.${file.status}") map { _ => Success(file.envelopeId) }
      file.data |>>> it
    }

    override def updateStatus(file: FileData) = {
      Future {
        Thread.sleep(500)
        new File(tmpDir).listFiles.filter(_.getName.startsWith(s"${file.envelopeId}-${file.fileId}.")).foreach {
          _.renameTo(new File(tmpDir, s"${file.envelopeId}-${file.fileId}.${file.status}"))
        }
      }
    }

    override def list(state: FileState): Future[Seq[FileData]] = {
      Future.successful {
        new File(tmpDir).listFiles.filter(_.getName.endsWith(s".$state")).toList.map { f =>
          FileData(Enumerator.fromFile(f), f.getName, "n/a", f.getName.split("-").init.mkString("-"), f.getName.split("-").last.split("\\.").init.mkString("."))
        }
      }
    }
  }

  def testFile = Enumerator.fromFile(new File("test/resources/testUpload.txt"))

  def toStringIteratee = Iteratee.fold[Array[Byte], String]("") { (s, d) => s ++ d.toString }

  def toFileIteratee(filename: String) = {
    val fos: FileOutputStream = new FileOutputStream(new File(filename))

    Iteratee.fold[Array[Byte], FileOutputStream](fos) { (f, d) => f.write(d); f } map { fos => fos.close() }
  }

  def cleanTmp() = {
    val tmpFiles = new File(tmpDir).listFiles()

    Seq(Unscanned, Scanning, Clean, VirusDetected).foreach { state =>
      tmpFiles.filter(_.getName.endsWith(s".$state")).foreach(_.delete())
    }
  }

  def file(name:String) = Try(Enumerator.fromFile(new File(s"test/resources/$name"))).getOrElse(Enumerator.empty)

  def filePart(name:String) = MultipartFormData.FilePart(name, name, Some("text/plain"), file(name))

  def createUploadRequest(successRedirectURL:Option[String] = Some("http://somewhere.com/success"),
                          failureRedirectURL:Option[String] = Some("http://somewhere.com/failure"),
                          envelopeId:Option[String] = Some(validEnvelopeId),
                          fileIds:Seq[String] = Seq("testUpload.txt"),
                          headers:Seq[(String, Seq[String])] = Seq()): FakeRequest[MultipartFormData[Enumerator[Array[Byte]]]] = {
    var params = Map[String, Seq[String]]()

    def addParam(paramName: String)(value:String) = params = params + (paramName -> Seq(value))

    successRedirectURL.foreach(addParam("successRedirect"))
    failureRedirectURL.foreach(addParam("failureRedirect"))
    envelopeId.foreach(addParam("envelopeId"))

    val multipartBody = MultipartFormData[Enumerator[Array[Byte]]](params, fileIds.map(filePart), Seq[BadPart](), Seq[MissingFilePart]())

    FakeRequest(method = "POST", uri = "/upload", headers = FakeHeaders(headers), body = multipartBody)
  }
}
