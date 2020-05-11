/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload

import akka.util.ByteString
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{MaxSizeExceeded, MultipartFormData, Request}
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.fileupload.s3.InMemoryMultipartFileHandler.FileCachedInMemory

object RestFixtures {

  type Multipart = MultipartFormData[FileCachedInMemory]

  def withSizeChecking(multipartBody: Multipart, sizeExceeded: Boolean): Either[MaxSizeExceeded, Multipart] = {
    if (sizeExceeded) {
      Left(MaxSizeExceeded(0))
    } else {
      Right(multipartBody)
    }
  }

  def uploadRequest(multipartBody: Multipart, sizeExceeded: Boolean) = {
    FakeRequest(method = "POST", uri = "/upload", headers = FakeHeaders(), body = withSizeChecking(multipartBody, sizeExceeded))
  }

  def emptyFileUploadRequest(filename: String) =
    uploadRequest(MultipartFormData(Map(),
      Seq(MultipartFormData.FilePart(filename, filename, Some("application/json"), FileCachedInMemory(ByteString.empty))),
      Seq.empty), sizeExceeded = false)

  def filePart(key: String, filename: String, contentType: Option[String]): FilePart[FileCachedInMemory] = {
    MultipartFormData.FilePart(key, filename, contentType, FileCachedInMemory(ByteString("foo")))
  }

  def validUploadRequest(files: Seq[File], sizeExceeded: Boolean = false): Request[scala.Either[MaxSizeExceeded, Multipart]] = {
    uploadRequest(MultipartFormData(Map(), files.map(file => filePart(file.filename, file.filename, file.contentType)),
      Seq.empty), sizeExceeded)
  }

  def multipartFormData(dataParts: Map[String, Seq[String]]) =
    uploadRequest(MultipartFormData(dataParts, files = List(), badParts = List()), sizeExceeded = false)
}
