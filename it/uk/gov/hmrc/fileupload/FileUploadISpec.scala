package uk.gov.hmrc.fileupload

import org.scalatest.Matchers
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import play.api.libs.ws.{WS, WSResponse}
import uk.gov.hmrc.fileupload.DomainFixtures._
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions, IntegrationSpec}

class FileUploadISpec extends IntegrationSpec with FileActions with EnvelopeActions with Eventually with Matchers {

  feature("File upload front-end") {

    val fileId = anyFileId
    val envelopeId = anyEnvelopeId

    scenario("transfer a file to the back-end") {
      responseToUpload(envelopeId, fileId)
      respondToEnvelopeCheck(envelopeId)

      val result = uploadDummyFile(envelopeId, fileId)

      result.status should be(200)

      quarantinedEventTriggered()
      eventually {
        fileScannedEventTriggered()
      }(PatienceConfig(timeout = Span(30, Seconds)))
      eventually {
        uploadedFile(envelopeId, fileId).map(_.getBodyAsString) shouldBe Some("someTextContents")
      }(PatienceConfig(timeout = Span(30, Seconds)))
    }

    scenario("""prevent uploading if envelope is not in "OPEN" state"""") {
      respondToEnvelopeCheck(envelopeId, body = ENVELOPE_CLOSED_RESPONSE)

      val result = uploadDummyFile(envelopeId, fileId)

      result.status should be(423)
    }

    def uploadDummyFile(envelopeId: EnvelopeId, fileId: FileId): WSResponse = {
      WS.url(s"http://localhost:$port/file-upload/upload/envelopes/$envelopeId/files/$fileId")
        .withHeaders("Content-Type" -> "multipart/form-data; boundary=---011000010111000001101001",
          "X-Request-ID" -> "someId",
          "X-Session-ID" -> "someId",
          "X-Requested-With" -> "someId")
        .post("-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"file1\"; filename=\"test.txt\"\r\nContent-Type: text/plain\r\n\r\nsomeTextContents\r\n-----011000010111000001101001--")
        .futureValue(PatienceConfig(timeout = Span(10, Seconds)))
    }

  }


}

