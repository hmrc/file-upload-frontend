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

package uk.gov.hmrc.fileupload.virusscan

import cats.data.Xor
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.iteratee.{Enumerator, Iteratee}
import uk.gov.hmrc.clamav.VirusDetectedException
import uk.gov.hmrc.fileupload.virusscan.ScanningService._
import uk.gov.hmrc.fileupload.{EnvelopeId, File, FileId}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

class ScanningServiceSpec extends UnitSpec with Matchers with ScalaFutures {

  def enumerator() = {
    val inputString = "a random string long enough to by divided into chunks"
    Enumerator[Array[Byte]](inputString.grouped(10).map(_.getBytes).toList: _*)
  }

  val sendingChunksSuccessful = (_ : Array[Byte]) => Future.successful(())
  val noVirusFound = () => Future.successful(Success(true))
  val virusDetected = () => Future.successful(Failure(new VirusDetectedException("test virus")))

  "Scanning service" should {

    "should fire a noVirusDetected event when a file is not infected" in {
      val fileContent: Array[Byte] = "test".getBytes
      val envelopeId: EnvelopeId = EnvelopeId("envid")
      val fileId: FileId = FileId("fileid")
      val file = File(data = Enumerator(fileContent), length = fileContent.size, filename = "test.txt", contentType = Some("application/text"), envelopeId = envelopeId, fileId = fileId )

      var collector: AnyRef = null
      val publisher =  (event:AnyRef) => {
        collector = event
      }
      val scanner = () => {
        Iteratee.fold[Array[Byte], Future[ScanResult]](Future.successful(Xor.Right(ScanResultFileClean))) { (result, bytes) => result }
      }

      ScanningService.scanBinaryData(scanner)(publisher)(file).futureValue

      collector shouldNot equal(null)
      collector.isInstanceOf[NoVirusDetected] shouldBe true
      val noVirusDetected = collector.asInstanceOf[NoVirusDetected]
      noVirusDetected.envelopeId shouldBe envelopeId
      noVirusDetected.fileId shouldBe fileId
    }

    "should fire a VirusDetected event when a file is infected" in {
      val fileContent: Array[Byte] = "Im a very bad virus".getBytes
      val envelopeId: EnvelopeId = EnvelopeId("envid")
      val fileId: FileId = FileId("fileid")
      val file = File(data = Enumerator(fileContent), length = fileContent.size, filename = "test.txt", contentType = Some("application/text"), envelopeId = envelopeId, fileId = fileId )

      var collector: AnyRef = null
      val publisher =  (event:AnyRef) => {
        collector = event
      }
      val scanner = () => {
        Iteratee.fold[Array[Byte], Future[ScanResult]](Future.successful(Xor.Left(ScanResultVirusDetected))) { (result, bytes) => result }
      }

      ScanningService.scanBinaryData(scanner)(publisher)(file).futureValue

      collector shouldNot equal(null)
      collector.isInstanceOf[VirusDetected] shouldBe true
      val virusDetected = collector.asInstanceOf[VirusDetected]
      virusDetected.envelopeId shouldBe envelopeId
      virusDetected.fileId shouldBe fileId
    }

  }

}
