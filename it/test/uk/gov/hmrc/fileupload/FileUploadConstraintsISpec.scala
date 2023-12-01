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

package uk.gov.hmrc.fileupload

import org.scalatest.GivenWhenThen
import uk.gov.hmrc.fileupload.DomainFixtures._
import uk.gov.hmrc.fileupload.support.FileActions

class FileUploadConstraintsISpec extends FileActions with GivenWhenThen {

  val fileId: FileId = anyFileId
  val envelopeId: EnvelopeId = anyEnvelopeId

  "Upload file of unsupported type that is not listed in content types specified in envelope" should {
    "Return 200 as contentTypes checking was not enabled" in {
      Given("Envelope created with specified contentTypes: application/pdf, image/jpeg and application/xml")
      Wiremock.respondToEnvelopeCheck(envelopeId, body = ENVELOPE_OPEN_RESPONSE)

      When("File uploaded is of an unsupported type")
      val result = uploadDummyUnsupportedContentTypeFile(envelopeId, fileId)

      Then("Return 200 as contentTypes checking was not enabled")
      result.status shouldBe 200
    }
  }

  "Prevent uploading file that is larger than maxSizePerItem specified in envelope" should {
    "Recieve 413 Entity Too Large" in {
      Given("Envelope created with specified maxSizePerItem: 10Mb")
      Wiremock.respondToEnvelopeCheck(envelopeId, body = ENVELOPE_OPEN_RESPONSE)

      When("Attempting to upload a file larger than 10MB")
      val result = uploadDummyLargeFile(envelopeId, fileId)

      Then("Will Recieve 413 Entity Too Large")
      result.status shouldBe 413
    }
  }
}
