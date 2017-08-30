package uk.gov.hmrc.fileupload

import org.scalatest.concurrent.Eventually
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
        Wiremock.scanFileCommandTriggered()
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
        Wiremock.scanFileCommandTriggered()
      }
      eventually {
        val res = download(envelopeId, fileId)
        res.status shouldBe 200
        res.body shouldBe "someTextContents"
      }
    }

    """Prevent uploading if envelope is not in "OPEN" state"""" in {
      Wiremock.respondToEnvelopeCheck(envelopeId, body = ENVELOPE_CLOSED_RESPONSE)

      val repository = new ChunksMongoRepository(mongo)
      repository.removeAll().futureValue
      def numberOfChunks = repository.findAll().futureValue.size
      numberOfChunks shouldBe 0

      val result = uploadDummyFile(envelopeId, fileId)
      result.status should be(423)
      numberOfChunks shouldBe 0
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

