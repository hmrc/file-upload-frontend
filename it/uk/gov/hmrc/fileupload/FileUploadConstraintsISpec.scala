package uk.gov.hmrc.fileupload

import org.scalatest.concurrent.Eventually
import org.scalatest.{FeatureSpecLike, GivenWhenThen, Matchers}
import uk.gov.hmrc.fileupload.DomainFixtures._
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions}

class FileUploadConstraintsISpec extends FeatureSpecLike with FileActions with EnvelopeActions with Eventually with Matchers with GivenWhenThen {

  val fileId = anyFileId
  val envelopeId = anyEnvelopeId

  feature("File Upload Frontend with Constraints") {

    scenario("Prevent uploading file that is larger than maxSizePerItem specified in envelope") {

      Given("Envelope created with specified maxSizePerItem: 10Mb")
      Wiremock.respondToEnvelopeCheck(envelopeId, body = ENVELOPE_OPEN_RESPONSE)

      When("Attempting to upload a file larger than 10MB")
      val result = uploadDummyLargeFile(envelopeId, fileId)

      Then("Will Recieve 413 Entity Too Large")
      result.status shouldBe 413
    }

    scenario("Upload file of unsupported type that is not listed in content types specified in envelope") {

      Given("Envelope created with specified contentTypes: application/pdf, image/jpeg and application/xml")
      Wiremock.respondToEnvelopeCheck(envelopeId, body = ENVELOPE_OPEN_RESPONSE)

      When("File uploaded is of an unsupported type")
      val result = uploadDummyUnsupportedContentTypeFile(envelopeId, fileId)

      Then("Return 200 as contentTypes checking was not enabled")
      result.status shouldBe 200

      Thread.sleep(500)
    }
  }
}
