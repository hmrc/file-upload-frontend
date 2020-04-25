/*
 * Copyright 2020 HM Revenue & Customs
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
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.iteratee.{Enumerator, Iteratee}
import uk.gov.hmrc.fileupload.quarantine.QuarantineService.QuarantineDownloadResult
import uk.gov.hmrc.fileupload.virusscan.ScanningService._
import uk.gov.hmrc.fileupload.{DomainFixtures, EnvelopeId, File, FileId, FileRefId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ScanningServiceSpec extends FlatSpec with Matchers with ScalaFutures with MockFactory {

  private val exception = new Exception("error")

  private val timeoutAttempts = 3

  "A successful scan" should behave like scanWithNumberOfAttempts(Xor.Right(ScanResultFileClean), 1)

  "A scan with a virus" should behave like scanWithNumberOfAttempts(Xor.Left(ScanResultVirusDetected), 1)

  "A scan with a failure sending chunks" should behave like scanWithNumberOfAttempts(Xor.Left(ScanResultFailureSendingChunks(exception)), 1)

  "A scan with an unexpected result" should behave like scanWithNumberOfAttempts(Xor.Left(ScanResultUnexpectedResult), 1)

  "A scan with an error result" should behave like scanWithNumberOfAttempts(Xor.Left(ScanResultError(exception)), 1)

  "A scan with multiple timeouts" should behave like scanWithNumberOfAttempts(Xor.Left(ScanReadCommandTimeOut), timeoutAttempts)

  "A scan with a timeout" should behave like timeoutFollowedByNonTimeout(Xor.Right(ScanResultFileClean), "clean", 2)

  it should behave like timeoutFollowedByNonTimeout(Xor.Left(ScanResultVirusDetected), "virus", 2)
  it should behave like timeoutFollowedByNonTimeout(Xor.Left(ScanResultFailureSendingChunks(exception)), "chunk failure", 2)
  it should behave like timeoutFollowedByNonTimeout(Xor.Left(ScanResultUnexpectedResult), "unexpected", 2)
  it should behave like timeoutFollowedByNonTimeout(Xor.Left(ScanResultError(exception)), "error", 2)
  it should behave like timeoutFollowedByNonTimeout(Xor.Left(ScanReadCommandTimeOut), "timeout", 3)

  def timeoutFollowedByNonTimeout(scanResult: ScanResult, scanResultType: String, attempts: Int) {
    it should s"make exactly $attempts when followed by a $scanResultType scan result" in {
      val scanner = stubFunction[Iteratee[Array[Byte], Future[ScanResult]]]

      scanner.when().returns(buildIteratee(Xor.Left(ScanReadCommandTimeOut))).noMoreThanOnce()
      scanner.when().returns(buildIteratee(scanResult))

      val result = ScanningService.scanBinaryData(scanner = scanner, scanTimeoutAttempts = timeoutAttempts, getFile = (_, _) => file)((_, _) => "some-key")(EnvelopeId(), FileId(), FileRefId()).futureValue

      result shouldBe scanResult

      scanner.verify().repeat(attempts)
    }
  }

  def scanWithNumberOfAttempts(scanResult: ScanResult, attempts: Int) {
    it should s"make exactly $attempts attempt[s] at scanning" in {
      val scanner = stubFunction[Iteratee[Array[Byte], Future[ScanResult]]]
      scanner.when().returns(buildIteratee(scanResult))

      val result = ScanningService.scanBinaryData(scanner = scanner, scanTimeoutAttempts = timeoutAttempts, getFile = (_, _) => file)((_, _) => "some-key")(EnvelopeId(), FileId(), FileRefId()).futureValue

      result shouldBe scanResult

      scanner.verify().repeat(attempts)
    }
  }

  private def file: Future[QuarantineDownloadResult] = {
    val tempFile = DomainFixtures.temporaryFile("file", Some("hello world"))
    Future.successful(Xor.right(File(Enumerator.fromFile(tempFile), 0, tempFile.getName, None)))
  }

  private def buildIteratee(result: ScanResult): Iteratee[Array[Byte], Future[ScanResult]] = {
    Iteratee.foldM[Array[Byte], Future[ScanResult]](Future.successful(result))((as, _) => Future.successful(as))
  }
}
