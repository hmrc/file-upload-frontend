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

package uk.gov.hmrc.fileupload_deprecated.services

import uk.gov.hmrc.fileupload_deprecated.connectors._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class UploadService(avScannerConnector: AvScannerConnector, fileUploadConnector: FileUploadConnector,
                    quarantineStoreConnector: QuarantineStoreConnector) extends ServicesConfig {

  import scala.concurrent.ExecutionContext.Implicits.global

  def updateStatusAndScan(file: FileData): Future[Try[Boolean]] = {
    for {
      newState <- quarantineStoreConnector.updateStatus(file, Scanning)
      r <- avScannerConnector.scan(newState.data)
    } yield r
  }

  def scanUnscannedFiles() = {
    quarantineStoreConnector.list(Unscanned).map { result =>
      result.foreach { file =>
        for {
          result <- updateStatusAndScan(file)
          r <- onScanComplete(file, result)
        } yield r
      }
    }
  }

  def onScanComplete(file: FileData, result: Try[Boolean]): Future[FileData] = {
    result match {
      case Success(_) => quarantineStoreConnector.updateStatus(file, Clean)
      case Failure(_) => quarantineStoreConnector.updateStatus(file, VirusDetected)
    }
  }

  def validateAndPersist(fileData: FileData)(implicit hc: HeaderCarrier) = {
    (for {
      validated <- fileUploadConnector.validate(fileData.envelopeId)
      persisted <- quarantineStoreConnector.persistFile(fileData)
    } yield {
      (validated, persisted)
    }) map {
      case result@(Success(_), Success(_)) => scanUnscannedFiles(); result
      case result => result
    }
  }
}