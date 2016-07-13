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

package uk.gov.hmrc.fileupload.services

import play.api.libs.iteratee.Enumerator
import uk.gov.hmrc.fileupload.connectors._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.util.{Success, Try}

trait UploadService extends FileUploadConnector with ServicesConfig {
  self: QuarantineStoreConnector with AvScannerConnector =>

  import scala.concurrent.ExecutionContext.Implicits.global

  def fileData: PartialFunction[FileData, Enumerator[Array[Byte]]] = { case f => f.data }

  def updateStatusAndScan(file: FileData): Future[Try[Boolean]] = {
    for {
      newState <- updateStatus(file, Scanning)
      r <- scan(newState.data)
    } yield r
  }

  def scanUnscannedFiles() = {
    list(Unscanned) map { _ foreach updateStatusAndScan }
  }

  def validateAndPersist(fileData: FileData)(implicit hc: HeaderCarrier) = {
    (for {
      validated <- validate(fileData.envelopeId)
      persisted <- persistFile(fileData)
    } yield {
      (validated, persisted)
    }) map {
      case result@(Success(_), Success(_)) => scanUnscannedFiles(); result
      case result => result
    }
  }
}