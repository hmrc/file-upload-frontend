package uk.gov.hmrc.fileupload

import org.scalatest.{FeatureSpecLike, Matchers}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import uk.gov.hmrc.fileupload.DomainFixtures._
import uk.gov.hmrc.fileupload.support._

class FileUploadISpec extends FeatureSpecLike with FileActions with EnvelopeActions with Eventually with Matchers{

  feature("File upload front-end") {

    val fileId = anyFileId
    val envelopeId = anyEnvelopeId

    scenario("transfer a file to the back-end") {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)

      val result = uploadDummyFile(envelopeId, fileId)

      result.status should be(200)

      Wiremock.quarantinedEventTriggered()
      eventually {
        Wiremock.fileScannedEventTriggered()
      }(PatienceConfig(timeout = Span(30, Seconds)))
      eventually {
        Wiremock.uploadedFile(envelopeId, fileId).map(_.getBodyAsString) shouldBe Some("someTextContents")
      }(PatienceConfig(timeout = Span(30, Seconds)))
    }

    scenario("""Prevent uploading if envelope is not in "OPEN" state"""") {
      Wiremock.respondToEnvelopeCheck(envelopeId, body = ENVELOPE_CLOSED_RESPONSE)

      val repository = new ChunksMongoRepository(mongo)
      repository.removeAll().futureValue
      def numberOfChunks = repository.findAll().futureValue.size
      numberOfChunks shouldBe 0

      val result = uploadDummyFile(envelopeId, fileId)
      result.status should be(423)
      numberOfChunks shouldBe 0
    }

    scenario("""Ensure we continue to allow uploading if envelope is in "OPEN" state"""") {
      Wiremock.respondToEnvelopeCheck(envelopeId, body = ENVELOPE_OPEN_RESPONSE)

      val repository = new ChunksMongoRepository(mongo)
      repository.removeAll().futureValue
      def numberOfChunks = repository.findAll().futureValue.size
      numberOfChunks shouldBe 0

      val result = uploadDummyFile(envelopeId, fileId)
      result.status should be(200)
      numberOfChunks should be > 0
    }

  }

}

