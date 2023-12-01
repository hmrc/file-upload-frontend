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

import java.io.InputStream

import play.api.Logger
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}
import uk.gov.hmrc.fileupload.quarantine.QuarantineService.QuarantineDownloadResult
import uk.gov.hmrc.fileupload.s3.S3KeyName

import scala.concurrent.{ExecutionContext, Future}

object ScanningService {

  type ScanResult = Either[ScanError, ScanResultFileClean.type]

  sealed trait ScanError

  case object ScanResultVirusDetected extends ScanError

  case object ScanReadCommandTimeOut extends ScanError

  case class ScanResultFailureSendingChunks(ex: Throwable) extends ScanError

  case object ScanResultUnexpectedResult extends ScanError

  case class ScanResultError(ex: Throwable) extends ScanError

  case object ScanResultFileClean

  type AvScan = (InputStream, Long) => Future[ScanResult]

  private val logger = Logger(getClass)

  def scanBinaryData(
    scanner            : AvScan,
    scanTimeoutAttempts: Int,
    getFile            : (S3KeyName, String) => Future[QuarantineDownloadResult]
  )(
    s3KeyAppender      : (EnvelopeId, FileId) => S3KeyName
  )(
    envelopeId         : EnvelopeId,
    fileId             : FileId,
    fileRefId          : FileRefId
  )(implicit
    ec: ExecutionContext
  ): Future[ScanResult] = {
    val appendedKey = s3KeyAppender(envelopeId, fileId)
    retries(scan(scanner, getFile(appendedKey, fileRefId.value)), fileId, scanTimeoutAttempts)
  }

  private def retries(
    scanResult         : => Future[ScanResult],
    fileId             : FileId,
    maximumScansAllowed: Int,
    scansAttempted     : Int                  = 1
  )(implicit
    ec: ExecutionContext
  ): Future[ScanResult] =
    scanResult.flatMap {
      case result if scansAttempted >= maximumScansAllowed =>
        logger.warn(s"Maximum scan retries attempted for fileId: $fileId ($scansAttempted of $maximumScansAllowed)")
        Future.successful(result)
      case Left(ScanReadCommandTimeOut) =>
        logger.error(s"Scan $scansAttempted of $maximumScansAllowed timed out for fileId $fileId")
        retries(scanResult, fileId, maximumScansAllowed, scansAttempted + 1)
      case result => Future.successful(result)
    }

  private def scan(
    scanner: AvScan,
    file   : Future[QuarantineDownloadResult]
  )(implicit
    ec: ExecutionContext
  ): Future[ScanResult] =
    file.flatMap {
      case Right(file) => scanner(file.data, file.length)
      case Left(e)     => Future.successful(Left(ScanResultError(new Exception(e.getClass.getSimpleName))))
    }
}
