package uk.gov.hmrc.fileupload

import org.scalatest.{FeatureSpecLike, Matchers}
import org.scalatest.concurrent.Eventually
import uk.gov.hmrc.fileupload.support.{ChunksMongoRepository, EnvelopeActions, FileActions}
import uk.gov.hmrc.fileupload.DomainFixtures._

class FileUploadConstraintsISpec extends FeatureSpecLike with FileActions with EnvelopeActions with Eventually with Matchers{

  val fileId = anyFileId
  val envelopeId = anyEnvelopeId

  feature("File Upload Frontend with Constraints") {

    scenario("Prevent uploading file that is larger than maxSizePerItem specified in envelope") {
      Wiremock.respondToEnvelopeCheck(envelopeId, body = ENVELOPE_OPEN_RESPONSE)

      val repository = new ChunksMongoRepository(mongo)
      repository.removeAll().futureValue
      def numberOfChunks = repository.findAll().futureValue.size
      numberOfChunks shouldBe 0

      val result = uploadDummyLargeFile(envelopeId, fileId)
      result.status shouldBe 413
      numberOfChunks shouldBe 0
    }

    scenario("Upload unsupported file type that is not listed in content types specified in envelope") {
      Wiremock.respondToEnvelopeCheck(envelopeId, body = ENVELOPE_OPEN_RESPONSE)

      val repository = new ChunksMongoRepository(mongo)
      repository.removeAll().futureValue
      def numberOfChunks = repository.findAll().futureValue.size
      numberOfChunks shouldBe 0

      val result = uploadDummyInvalidContentTypeFile(envelopeId, fileId)
      result.underlying
      result.status shouldBe 200
      numberOfChunks shouldBe 0
    }
  }

}
