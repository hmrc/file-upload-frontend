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
