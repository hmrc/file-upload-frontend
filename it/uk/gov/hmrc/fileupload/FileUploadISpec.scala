package uk.gov.hmrc.fileupload

import play.api.libs.ws.WS
import uk.gov.hmrc.fileupload.DomainFixtures._
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions, IntegrationSpec}
import uk.gov.hmrc.fileupload.transfer.{FakeAuditer, FakeFileUploadBackend}

/**
  * Integration tests for FILE-100
  * Update FileMetadata
  *
  */
class FileUploadISpec extends IntegrationSpec with FileActions with EnvelopeActions with FakeFileUploadBackend with FakeAuditer {

  feature("File upload front-end") {
    scenario("transfer a file to the back-end") {
      val fileId = anyFileId
      val envelopeId = anyEnvelopeId

      responseToUpload(envelopeId, fileId)
      stubResponseForSendMetadata(envelopeId, fileId)
      respondToEnvelopeCheck(envelopeId)

      val result = WS.url(s"http://localhost:$port/file-upload/upload/envelope/${envelopeId.value}/file/${fileId.value}")
          .withHeaders("Content-Type" -> "multipart/form-data; boundary=---011000010111000001101001",
            "X-Request-ID" -> "someId",
            "X-Session-ID" -> "someId",
            "X-Requested-With" -> "someId")
          .post("-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"file1\"; filename=\"test.txt\"\r\nContent-Type: text/plain\r\n\r\nsomeTextContents\r\n-----011000010111000001101001--")
          .futureValue

      result.status should be(200)

      uploadedFile(envelopeId, fileId).map(_.getBodyAsString) shouldBe Some("someTextContents")

      eventQuarantinedTriggered()
    }
  }
}

