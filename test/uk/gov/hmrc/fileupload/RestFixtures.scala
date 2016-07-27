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

package uk.gov.hmrc.fileupload

import java.nio.file.{Files, Paths}

import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.fileupload.DomainFixtures.anyFile

object RestFixtures {

  def uploadRequest(multipartBody: MultipartFormData[TemporaryFile]) = {
    FakeRequest(method = "POST", uri = "/upload", headers = FakeHeaders(), body = multipartBody)
  }

  def validUploadRequest(file: File = anyFile()) = {
    uploadRequest(MultipartFormData(Map("envelopeId" -> Seq(file.envelopeId.value), "fileId" -> Seq(file.fileId.value)),
      Seq(MultipartFormData.FilePart(file.filename, file.filename, file.contentType,
        TemporaryFile(Files.write(Paths.get(file.filename), file.data).toFile))),
      Seq.empty, Seq.empty))
  }
}
