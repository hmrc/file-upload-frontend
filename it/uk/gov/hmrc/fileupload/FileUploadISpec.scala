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



  }


}

