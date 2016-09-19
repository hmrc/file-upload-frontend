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
import uk.gov.hmrc.fileupload.quarantine.QuarantineService.QuarantineDownloadResult
import uk.gov.hmrc.fileupload.FileRefId

import scala.concurrent.{ExecutionContext, Future}

object ScanningService {

  type ScanResult = Xor[ScanError, ScanResultFileClean.type]

  sealed trait ScanError
  case object ScanResultVirusDetected extends ScanError
  case class  ScanResultFailureSendingChunks(ex: Throwable) extends ScanError
  case object ScanResultUnexpectedResult extends ScanError
  case class  ScanResultError(ex: Throwable) extends ScanError
  case object ScanResultFileClean

  type AvScanIteratee = Iteratee[Array[Byte], Future[ScanResult]]

  def scanBinaryData(scanner: () => Iteratee[Array[Byte], Future[ScanResult]], getFile: (FileRefId) => Future[QuarantineDownloadResult])
                    (fileRefId: FileRefId)
                    (implicit ec: ExecutionContext): Future[ScanResult] =
    getFile(fileRefId).flatMap {
      case Xor.Right(file) => file.streamTo(scanner()).flatMap(identity)
      case Xor.Left(e) => Future.successful(Xor.Left(ScanResultError(new Exception(e.getClass.getSimpleName))))
    }
}
