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
import uk.gov.hmrc.clamav.model.ScanningResult
import uk.gov.hmrc.fileupload.utils.NonFatalWithLogging
import uk.gov.hmrc.fileupload.virusscan.ScanningService._

import scala.concurrent.{ExecutionContext, Future}

class VirusScanner(avClient: AvClient):

  private val logger = Logger(getClass)

  private val commandReadTimedOutMessage = "COMMAND READ TIMED OUT"

  def scan(using ExecutionContext): AvScan =
    scanWith(avClient.sendAndCheck)

  private[virusscan] def scanWith(
    sendAndCheck: (InputStream, Int) => Future[ScanningResult]
  )(is    : InputStream,
    length: Long
  )(using
    ExecutionContext
  ): Future[ScanResult] =
    sendAndCheck(is, length.toInt)
      .map:
        case ScanningResult.Clean             => Right(ScanResultFileClean)
        case ScanningResult.Infected(message) if message.contains(commandReadTimedOutMessage) =>
                                                 Left(ScanError.ScanReadCommandTimeOut)
        case ScanningResult.Infected(message) => logger.warn(s"File is infected: [$message].")
                                                 Left(ScanError.ScanResultVirusDetected)
      .recover:
        case NonFatalWithLogging(ex) => Left(ScanError.ScanResultError(ex))
