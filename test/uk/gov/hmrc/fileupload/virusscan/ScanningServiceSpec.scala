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

import java.net.SocketException

import cats.data.Xor
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.iteratee.Enumerator
import uk.gov.hmrc.clamav.{ClamAntiVirus, VirusDetectedException}
import uk.gov.hmrc.fileupload.virusscan.ScanningService._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Random, Success}

class ScanningServiceSpec extends UnitSpec with Matchers with ScalaFutures {

  def enumerator() = {
    val inputString = "a random string long enough to by divided into chunks"
    Enumerator[Array[Byte]](inputString.grouped(10).map(_.getBytes).toList: _*)
  }

  val sendingChunksSuccessful = (_ : Array[Byte]) => Future.successful(())
  val noVirusFound = () => Future.successful(Success(true))
  val virusDetected = () => Future.successful(Failure(new VirusDetectedException("test virus")))

  "Scanning service" should {

    s"return $ScanResultFileClean result if input did not contain a virus" in {
      val chunkEnumerator = enumerator()

      val result = await(chunkEnumerator.run(ScanningService.scanIteratee(sendingChunksSuccessful, noVirusFound)).flatMap(identity))

      result shouldBe Xor.Right(ScanResultFileClean)
    }

    s"return $ScanResultVirusDetected result if input contained a virus" in {
      val chunkEnumerator = enumerator()

      val result = await(chunkEnumerator.run(ScanningService.scanIteratee(sendingChunksSuccessful, virusDetected)).flatMap(identity))

      result shouldBe Xor.Left(ScanResultVirusDetected)
    }

    s"return $ScanResultError result if checkForVirus returned unexpected Success(false) which should never happen" in {
      val chunkEnumerator = enumerator()
      val unexpectedResultFromCheckingForAVirus = () => Future.successful(Success(false))

      val result = await(chunkEnumerator.run(ScanningService.scanIteratee(sendingChunksSuccessful, unexpectedResultFromCheckingForAVirus)).flatMap(identity))

      result shouldBe Xor.Left(ScanResultUnexpectedResult)
    }

    s"return $ScanResultError result if $ClamAntiVirus returned an unanticipated error" in {
      val chunkEnumerator = enumerator()
      val exception = new RuntimeException("av-client error")
      val avClientReturnedErrorWhenCheckingForAVirus = () => Future.successful(Failure(exception))

      val result = await(chunkEnumerator.run(ScanningService.scanIteratee(sendingChunksSuccessful, avClientReturnedErrorWhenCheckingForAVirus)).flatMap(identity))

      result shouldBe Xor.Left(ScanResultError(exception))
    }

    s"return $ScanResultError result if calling checkForVirus failed (e.g. network issue)" in {
      val chunkEnumerator = enumerator()
      val exception = new SocketException("failed to connect to clam av")
      val problemCheckingForAVirus = () => Future.failed(exception)

      val result = await(chunkEnumerator.run(ScanningService.scanIteratee(sendingChunksSuccessful, problemCheckingForAVirus)).flatMap(identity))

      result shouldBe Xor.Left(ScanResultError(exception))
    }

    s"return $ScanResultFailureSendingChunks result if calling sendChunk failed (e.g. network issue)" in {
      val chunkEnumerator = enumerator()
      val exception = new RuntimeException("unknown error")
      val sendingChunksFailed = (_ : Array[Byte]) => Future.failed(exception)

      val result = await(chunkEnumerator.run(ScanningService.scanIteratee(sendingChunksFailed, noVirusFound)).flatMap(identity))

      result shouldBe Xor.Left(ScanResultFailureSendingChunks(exception))
    }

  }

}
