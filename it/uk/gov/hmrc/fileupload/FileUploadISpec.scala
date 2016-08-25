package uk.gov.hmrc.fileupload

import play.api.libs.ws.WS
import uk.gov.hmrc.fileupload.DomainFixtures._
import uk.gov.hmrc.fileupload.RestFixtures.validUploadRequest
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions, IntegrationSpec}
import uk.gov.hmrc.fileupload.transfer.FakeFileUploadBackend

/**
  * Integration tests for FILE-100
  * Update FileMetadata
  *
  */
class FileUploadISpec extends IntegrationSpec with FileActions with EnvelopeActions with FakeFileUploadBackend {

  feature("File upload front-end") {
    ignore("transfer a file to the back-end") {
      val fileContents = "someTextContents"
      val tempFile = temporaryTexFile(Some(fileContents))
      val file = anyFileFor(file = tempFile)
      val request = validUploadRequest(file)
      responseToUpload(file.envelopeId, file.fileId)
      respondToEnvelopeCheck(file.envelopeId)

      WS
        .url(s"http://localhost:9000/file-upload-frontend/envelope/$file.envelopeId/file/${file.fileId}/metadata" )
        .withHeaders("Content-Type" -> "application/json")
        .put(fileContents.getBytes())
        .futureValue

      uploadedFile(file.envelopeId, file.fileId).map(_.getBodyAsString) shouldBe Some(fileContents)

      eventTriggered()
    }
  }
}

