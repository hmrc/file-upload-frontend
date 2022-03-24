/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.utils

import play.api.http.HeaderNames
import play.api.mvc.{MultipartFormData, Request}
import uk.gov.hmrc.fileupload.s3.InMemoryMultipartFileHandler
import uk.gov.hmrc.fileupload.s3.InMemoryMultipartFileHandler.FileCachedInMemory

case class LoggerValues(fileExtension: String, userAgent: String)

trait LoggerHelper {
  def getLoggerValues(formData: MultipartFormData.FilePart[InMemoryMultipartFileHandler.FileCachedInMemory],
                      request: Request[_]): LoggerValues
}

class LoggerHelperFileExtensionAndUserAgent extends LoggerHelper {
  def getLoggerValues(formData: MultipartFormData.FilePart[InMemoryMultipartFileHandler.FileCachedInMemory],
                      request: Request[_]): LoggerValues =
    LoggerValues(fileExtensionFromFileName(formData), userAgentFromRequest(request))

  private def fileExtensionFromFileName(file: MultipartFormData.FilePart[FileCachedInMemory]): String = {
    val parts = Option(file.filename).map(_.split("\\.").toList).getOrElse(Nil)
    parts.length match {
      case 0 | 1 => "no-file-type"
      case _ => parts.last.toLowerCase
    }
  }

  private def userAgentFromRequest(request: Request[_]): String =
    request.headers.get(HeaderNames.USER_AGENT).getOrElse("no-user-agent")
}
