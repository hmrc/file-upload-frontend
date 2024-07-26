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

import org.scalatest.concurrent.Eventually
import play.api.libs.ws.DefaultBodyReadables.readableAsString
import uk.gov.hmrc.fileupload.DomainFixtures._
import uk.gov.hmrc.fileupload.support._

class FileUploadISpec extends FileActions with EnvelopeActions with Eventually {

  "File upload front-end" should {
    val fileId = anyFileId
    val envelopeId = anyEnvelopeId

    "transfer a file to the back-end" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)

      val result = uploadDummyFile(envelopeId, fileId)

      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.markFileAsCleanCommandTriggered()
      }
      eventually {
        val res = download(envelopeId, fileId)
        res.status shouldBe 200
        res.body shouldBe "someTextContents"
      }
    }

    "can retrieve a file from the internal download endpoint" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)

      val result = uploadDummyFile(envelopeId, fileId)

      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.markFileAsCleanCommandTriggered()
      }
      eventually {
        val res = download(envelopeId, fileId)
        res.status shouldBe 200
        res.body shouldBe "someTextContents"
      }
    }

    """Prevent uploading if envelope is not in "OPEN" state"""" in {
      Wiremock.respondToEnvelopeCheck(envelopeId, body = ENVELOPE_CLOSED_RESPONSE)

      val result = uploadDummyFile(envelopeId, fileId)
      result.status should be(423)
    }

    """Ensure we continue to allow uploading if envelope is in "OPEN" state"""" in {
      val secondFileId = anyFileId
      Wiremock.respondToEnvelopeCheck(envelopeId, body = ENVELOPE_OPEN_RESPONSE)

      val result = uploadDummyFile(envelopeId, secondFileId)
      result.status should be(200)

      eventually {
        val res = download(envelopeId, secondFileId)
        res.status shouldBe 200
      }
    }
  }
}
