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

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.scanFileCommandTriggered()
      }(PatienceConfig(timeout = Span(30, Seconds)))
      eventually {
        val res = download(envelopeId, fileId)
        res.status shouldBe 200
        res.body shouldBe "someTextContents"

      }(PatienceConfig(timeout = Span(30, Seconds)))
    }

    scenario("can retrieve a file from the internal download endpoint") {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)

      val result = uploadDummyFile(envelopeId, fileId)

      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.scanFileCommandTriggered()
      }(PatienceConfig(timeout = Span(30, Seconds)))
      eventually {
        val res = download(envelopeId, fileId)
        res.status shouldBe 200
        res.body shouldBe "someTextContents"

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

      val secondFileId = anyFileId
      Wiremock.respondToEnvelopeCheck(envelopeId, body = ENVELOPE_OPEN_RESPONSE)

      val result = uploadDummyFile(envelopeId, secondFileId)
      result.status should be(200)

      eventually {
        val res = download(envelopeId, secondFileId)
        res.status shouldBe 200

      }(PatienceConfig(timeout = Span(30, Seconds)))
    }

  }

}

