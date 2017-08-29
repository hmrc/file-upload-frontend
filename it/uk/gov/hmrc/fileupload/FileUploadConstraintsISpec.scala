package uk.gov.hmrc.fileupload

import org.scalatest.{FeatureSpecLike, GivenWhenThen, Matchers}
import org.scalatest.concurrent.Eventually
import uk.gov.hmrc.fileupload.support.{ChunksMongoRepository, EnvelopeActions, FileActions}
import uk.gov.hmrc.fileupload.DomainFixtures._

class FileUploadConstraintsISpec extends FeatureSpecLike with FileActions with EnvelopeActions with Eventually with Matchers with GivenWhenThen{

  val fileId = anyFileId
  val envelopeId = anyEnvelopeId

  feature("File Upload Frontend with Constraints") {

    val repository = new ChunksMongoRepository(mongo)
    repository.removeAll().futureValue
    def numberOfChunks = repository.findAll().futureValue.size

    scenario("Upload file of unsupported type that is not listed in content types specified in envelope") {
      Given("Envelope created with specified contentTypes: application/pdf, image/jpeg and application/xml")
      Wiremock.respondToEnvelopeCheck(envelopeId, body = ENVELOPE_OPEN_RESPONSE)

      When("File uploaded is of an unsupported type")
      val result = uploadDummyUnsupportedContentTypeFile(envelopeId, fileId)

      Then("The contentTypes checking was not enabled so Return 200")
      result.status shouldBe 200
    }

    scenario("Prevent uploading file that is larger than maxSizePerItem specified in envelope") {
      Given("Envelope created with specified maxSizePerItem: 10Mb")
      Wiremock.respondToEnvelopeCheck(envelopeId, body = ENVELOPE_OPEN_RESPONSE)

      When("Attempting to upload a file larger than 10MB")
      val result = uploadDummyLargeFile(envelopeId, fileId)

      Then("Will Recieve 413 Entity Too Large")
      result.status shouldBe 413

      And("No chunks created because file was not uploaded")
      numberOfChunks shouldBe 0
    }


  }

}
