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

import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.ArgumentMatchers.any
import uk.gov.hmrc.fileupload.quarantine.QuarantineService.QuarantineDownloadResult
import uk.gov.hmrc.fileupload.s3.S3KeyName
import uk.gov.hmrc.fileupload.virusscan.ScanningService._
import uk.gov.hmrc.fileupload.{DomainFixtures, EnvelopeId, File, FileId, FileRefId}

import java.io.{FileInputStream, InputStream}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ScanningServiceSpec
  extends AnyFlatSpec
     with Matchers
     with MockitoSugar
     with ScalaFutures
     with IntegrationPatience {

  private val exception = Exception("error")

  private val timeoutAttempts = 3

  "A successful scan" should behave like scanWithNumberOfAttempts(Right(ScanResultFileClean), 1)

  "A successful scan with 0 scanTimeoutAttempts" should behave like scanWithNumberOfAttempts(Right(ScanResultFileClean), 1, 0)

  "A successful scan with -1 scanTimeoutAttempts" should behave like scanWithNumberOfAttempts(Right(ScanResultFileClean), 1, -1)

  "A scan with a virus" should behave like scanWithNumberOfAttempts(Left(ScanError.ScanResultVirusDetected), 1)

  "A scan with a failure sending chunks" should behave like scanWithNumberOfAttempts(Left(ScanError.ScanResultFailureSendingChunks(exception)), 1)

  "A scan with an unexpected result" should behave like scanWithNumberOfAttempts(Left(ScanError.ScanResultUnexpectedResult), 1)

  "A scan with an error result" should behave like scanWithNumberOfAttempts(Left(ScanError.ScanResultError(exception)), 1)

  "A scan with multiple timeouts" should behave like scanWithNumberOfAttempts(Left(ScanError.ScanReadCommandTimeOut), timeoutAttempts)

  "A scan with a timeout" should behave like timeoutFollowedByNonTimeout(Right(ScanResultFileClean), "clean", 2)

  it should behave like timeoutFollowedByNonTimeout(Left(ScanError.ScanResultVirusDetected), "virus", 2)
  it should behave like timeoutFollowedByNonTimeout(Left(ScanError.ScanResultFailureSendingChunks(exception)), "chunk failure", 2)
  it should behave like timeoutFollowedByNonTimeout(Left(ScanError.ScanResultUnexpectedResult), "unexpected", 2)
  it should behave like timeoutFollowedByNonTimeout(Left(ScanError.ScanResultError(exception)), "error", 2)
  it should behave like timeoutFollowedByNonTimeout(Left(ScanError.ScanReadCommandTimeOut), "timeout", 3)

  def timeoutFollowedByNonTimeout(scanResult: ScanResult, scanResultType: String, attempts: Int): Unit = {
    it should s"make exactly $attempts when followed by a $scanResultType scan result" in {
      val scanner = mock[(InputStream, Long) => Future[ScanResult]]

      when(scanner.apply(any[InputStream], any[Long]))
        .thenReturn(
          Future.successful(Left(ScanError.ScanReadCommandTimeOut)),
        Future.successful(scanResult))

      val result = ScanningService.scanBinaryData(scanner = scanner, scanTimeoutAttempts = timeoutAttempts, getFile = (_, _) => file)((_, _) => S3KeyName("some-key"))(EnvelopeId(), FileId(), FileRefId()).futureValue

      result shouldBe scanResult

      verify(scanner, times(attempts)).apply(any, any)
    }
  }

  def scanWithNumberOfAttempts(scanResult: ScanResult, attempts: Int, maxNumberOfAttemps: Int = timeoutAttempts): Unit = {
    it should s"make exactly $attempts attempt[s] at scanning" in {
      val scanner = mock[(InputStream, Long) => Future[ScanResult]]

      when(scanner.apply(any[InputStream], any[Long]))
        .thenReturn(Future.successful(scanResult))

      val result = ScanningService.scanBinaryData(scanner = scanner, scanTimeoutAttempts = maxNumberOfAttemps, getFile = (_, _) => file)((_, _) => S3KeyName("some-key"))(EnvelopeId(), FileId(), FileRefId()).futureValue

      result shouldBe scanResult

      verify(scanner, times(attempts)).apply(any, any)
    }
  }

  private def file: Future[QuarantineDownloadResult] = {
    val tempFile = DomainFixtures.temporaryFile("file", Some("hello world"))
    Future.successful(Right(File(FileInputStream(tempFile), 0, tempFile.getName, None)))
  }
}
