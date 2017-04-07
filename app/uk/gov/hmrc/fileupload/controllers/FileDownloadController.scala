/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.controllers

import play.api.Logger
import play.api.http.HttpEntity
import play.api.mvc._
import uk.gov.hmrc.fileupload.s3.S3KeyName
import uk.gov.hmrc.fileupload.s3.S3Service.DownloadFromTransient
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

import scala.concurrent.ExecutionContext

class FileDownloadController(
  downloadFromTransient: DownloadFromTransient,
  createS3Key: (EnvelopeId, FileId) => S3KeyName,
  now: () => Long)
  (implicit executionContext: ExecutionContext) extends Controller {

  def download(envelopeId: EnvelopeId, fileId: FileId) = Action { implicit request =>
    val key = createS3Key(envelopeId, fileId)
    val result = downloadFromTransient(key)

    Logger.info(s"download result: contentType: ${result.metadata.contentType} &  length: ${result.metadata.contentLength}, metadata: ${result.metadata.s3Metadata}")

    Result(
      header = ResponseHeader(200, Map.empty),
      body = HttpEntity.Streamed(
        result.stream,
        Some(result.metadata.contentLength),
        Some(result.metadata.contentType))
    )
  }
}
