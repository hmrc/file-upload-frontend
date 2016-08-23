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
import play.api.libs.iteratee.Iteratee
import uk.gov.hmrc.clamav.config.ClamAvConfig
import uk.gov.hmrc.clamav.{ClamAntiVirus, VirusDetectedException}
import uk.gov.hmrc.fileupload.ServiceConfig
import uk.gov.hmrc.fileupload.utils.NonFatalWithLogging
import uk.gov.hmrc.fileupload.virusscan.ScanningService._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object VirusScanner {
  private def clamAvConfig = ClamAvConfig(ServiceConfig.clamAvConfig)

  def scanIteratee()(implicit ec: ExecutionContext): AvScanIteratee = {
    val clamAntiVirus = ClamAntiVirus(clamAvConfig)
    scanIteratee(clamAntiVirus.send, clamAntiVirus.checkForVirus)
  }

  private[virusscan] def scanIteratee(sendChunk: Array[Byte] => Future[Unit], checkForVirus: () => Future[Try[Boolean]])
                                     (implicit ec: ExecutionContext): AvScanIteratee =
    Iteratee.fold[Array[Byte],Future[Unit]](Future.successful(())) { (previousResult, chunk) =>
      previousResult.flatMap(_ => sendChunk(chunk))
    }.map {
      resultOfSendingChunks => resultOfSendingChunks.flatMap { _ =>
        checkForVirus().map {
          case Success(true) => Xor.right(ScanResultFileClean)
          case Success(false) => Xor.left(ScanResultUnexpectedResult) // should never happen as client only returns Success(true)...
          case Failure(_ : VirusDetectedException) => Xor.left(ScanResultVirusDetected)
          case Failure(NonFatalWithLogging(ex)) => Xor.left(ScanResultError(ex))
        }.recover {
          case NonFatalWithLogging(ex) => Xor.left(ScanResultError(ex))
        }
      }.recover {
        case NonFatalWithLogging(ex) => Xor.left(ScanResultFailureSendingChunks(ex))
      }
    }
}
