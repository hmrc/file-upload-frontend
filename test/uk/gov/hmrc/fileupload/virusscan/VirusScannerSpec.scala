/*
 * Copyright 2023 HM Revenue & Customs
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

import java.net.SocketException
import java.io.{ByteArrayInputStream, InputStream}

import org.apache.pekko.actor.ActorSystem
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.clamav.model.{Clean, Infected, ScanningResult}
import uk.gov.hmrc.fileupload.TestApplicationComponents
import uk.gov.hmrc.fileupload.virusscan.ScanningService._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class VirusScannerSpec
  extends AnyWordSpec
     with Matchers
     with ScalaFutures
     with TestApplicationComponents
     with IntegrationPatience {

  implicit val as: ActorSystem = ActorSystem("test")

  val inputString = "a random string long enough to by divided into chunks"
  private def fileSource: InputStream = new ByteArrayInputStream(inputString.getBytes)
  val fileLength = inputString.getBytes.length

  def clean(inputstream: InputStream, length: Int) = Future.successful(Clean)
  def virusDetected(inputstream: InputStream, length: Int) = Future.successful(Infected("test virus"))
  def commandTimeout(inputstream: InputStream, length: Int) = Future.successful(Infected("COMMAND READ TIMED OUT"))
  def failWith(exception: Exception)(inputStream: InputStream, length: Int): Future[ScanningResult] = Future.failed(exception)

  val virusScanner = new VirusScanner(app.injector.instanceOf[AvClient])

  "VirusScanner" should {
    s"return $ScanResultFileClean result if input did not contain a virus" in {
      val result = virusScanner.scanWith(sendAndCheck = clean)(fileSource, fileLength).futureValue
      result shouldBe Right(ScanResultFileClean)
    }

    s"return $ScanResultVirusDetected result if input contained a virus" in {
      val result = virusScanner.scanWith(sendAndCheck = virusDetected)(fileSource, fileLength).futureValue
      result shouldBe Left(ScanResultVirusDetected)
    }

    //clamav client unfortunately returns this as an Infection
    s"return $ScanReadCommandTimeOut result if exception indicates command read timeout" in {
      val result = virusScanner.scanWith(sendAndCheck = commandTimeout)(fileSource, fileLength).futureValue
      result shouldBe Left(ScanReadCommandTimeOut)
    }

    s"return $ScanResultError result if ClamAntiVirus returned an unanticipated error" in {
      val exception = new RuntimeException("av-client error")
      val result = virusScanner.scanWith(sendAndCheck = failWith(exception))(fileSource, fileLength).futureValue
      result shouldBe Left(ScanResultError(exception))
    }

    s"return $ScanResultError result if calling checkForVirus failed (e.g. network issue)" in {
      val exception = new SocketException("failed to connect to clam av")
      val result = virusScanner.scanWith(sendAndCheck = failWith(exception))(fileSource, fileLength).futureValue
      result shouldBe Left(ScanResultError(exception))
    }
  }
}
