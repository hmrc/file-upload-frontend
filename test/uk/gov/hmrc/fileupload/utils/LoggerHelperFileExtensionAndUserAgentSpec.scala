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

package uk.gov.hmrc.fileupload.utils

import org.apache.pekko.util.ByteString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.mvc.MultipartFormData
import play.api.test.FakeRequest
import uk.gov.hmrc.fileupload.s3.InMemoryMultipartFileHandler.FileCachedInMemory

class LoggerHelperFileExtensionAndUserAgentSpec
  extends AnyWordSpecLike
     with Matchers {

  "LoggerHelper" should {
    "correctly parse a file extension and user agents into strings when both are set" in {
      val inMemoryFile: FileCachedInMemory = FileCachedInMemory(ByteString("Hello World"))
      val formData = new MultipartFormData.FilePart[FileCachedInMemory](
        key = "12345",
        filename = "my-file.docx.pdf",
        contentType = None,
        ref = inMemoryFile
      )

      val request = FakeRequest().withHeaders(("User-Agent", "soft-drinks-industry-levy"))

      val loggerHelper = new LoggerHelperFileExtensionAndUserAgent
      val result = loggerHelper.getLoggerValues(formData, request)
      result shouldBe LoggerValues("pdf", "soft-drinks-industry-levy")
    }

    "correctly parse an upper case file extension and user agents into strings when both are set" in {
      val inMemoryFile: FileCachedInMemory = FileCachedInMemory(ByteString("Hello World"))
      val formData = new MultipartFormData.FilePart[FileCachedInMemory](
        key = "12345",
        filename = "my-file.docx.PDF",
        contentType = None,
        ref = inMemoryFile
      )

      val request = FakeRequest().withHeaders(("User-Agent", "soft-drinks-industry-levy"))

      val loggerHelper = new LoggerHelperFileExtensionAndUserAgent
      val result = loggerHelper.getLoggerValues(formData, request)
      result shouldBe LoggerValues("pdf", "soft-drinks-industry-levy")
    }

    "correctly parse a file without a file extension and user agents into strings" in {
      val inMemoryFile: FileCachedInMemory = FileCachedInMemory(ByteString("Hello World"))
      val formData = new MultipartFormData.FilePart[FileCachedInMemory](
        key = "12345",
        filename = "my-file-123",
        contentType = None,
        ref = inMemoryFile
      )

      val request = FakeRequest().withHeaders(("User-Agent", "soft-drinks-industry-levy"))

      val loggerHelper = new LoggerHelperFileExtensionAndUserAgent
      val result = loggerHelper.getLoggerValues(formData, request)
      result shouldBe LoggerValues("no-file-type", "soft-drinks-industry-levy")
    }

    "correctly parse a file without a file name and user agents into strings" in {
      val inMemoryFile: FileCachedInMemory = FileCachedInMemory(ByteString("Hello World"))
      val formData = new MultipartFormData.FilePart[FileCachedInMemory](
        key = "12345",
        filename = null,
        contentType = None,
        ref = inMemoryFile
      )

      val request = FakeRequest().withHeaders(("User-Agent", "soft-drinks-industry-levy"))

      val loggerHelper = new LoggerHelperFileExtensionAndUserAgent
      val result = loggerHelper.getLoggerValues(formData, request)
      result shouldBe LoggerValues("no-file-type", "soft-drinks-industry-levy")
    }

    "correctly parse a file extension and no user agents into strings" in {
      val inMemoryFile: FileCachedInMemory = FileCachedInMemory(ByteString("Hello World"))
      val formData = new MultipartFormData.FilePart[FileCachedInMemory](
        key = "12345",
        filename = "my-file.docx.pdf",
        contentType = None,
        ref = inMemoryFile
      )

      val request = FakeRequest()

      val loggerHelper = new LoggerHelperFileExtensionAndUserAgent
      val result = loggerHelper.getLoggerValues(formData, request)
      result shouldBe LoggerValues("pdf", "no-user-agent")
    }
  }
}
