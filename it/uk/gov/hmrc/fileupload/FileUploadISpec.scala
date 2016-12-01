package uk.gov.hmrc.fileupload

import org.scalatest.Matchers
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import play.api.libs.ws.{WS, WSResponse}
import uk.gov.hmrc.fileupload.DomainFixtures._
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions, IntegrationSpec}
import uk.gov.hmrc.mongo.MongoSpecSupport

class FileUploadISpec extends IntegrationSpec with FileActions with EnvelopeActions with Eventually with Matchers {

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

    scenario("""prevent uploading if envelope is not in "OPEN" state"""") {

      // Check no chunks exist in mongo related to the closed envelope
      // upload dummy file
      // Check 423 response
      // Check no chunks have been written to mongo

      Wiremock.respondToEnvelopeCheck(envelopeId, body = ENVELOPE_CLOSED_RESPONSE)

      val result = uploadDummyFile(envelopeId, fileId)

      result.status should be(423)
    }



  }


}

