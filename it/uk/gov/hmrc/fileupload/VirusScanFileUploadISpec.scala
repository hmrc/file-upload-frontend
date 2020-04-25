package uk.gov.hmrc.fileupload

import org.scalatest.concurrent.Eventually
import uk.gov.hmrc.fileupload.DomainFixtures.{anyEnvelopeId, anyFileId}
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions, StubClamAntiVirus}

class VirusScanFileUploadISpec extends FileActions with EnvelopeActions with Eventually with StubClamAntiVirus {

  "File upload front-end" should {
    val fileId = anyFileId
    val envelopeId = anyEnvelopeId

    "transfer a file to the back-end if virus scanning succeeds" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)

      val result = uploadDummyFile(envelopeId, fileId)

      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.scanFileCommandTriggered()
      }
      numberOfScansAttempted shouldBe 1
    }

    "not retry virus scanning fails if general error occurs" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      setVirusDetected("Some virus detected", timesToReturnVirus = 100)

      val result = uploadDummyFile(envelopeId, fileId)

      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.scanFileCommandTriggered()
      }

      numberOfScansAttempted shouldBe 1
    }

    "retry virus scanning fails if COMMAND READ TIMED OUT occurs until successful" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      setVirusDetected("COMMAND READ TIMED OUT", timesToReturnVirus = 1)

      val result = uploadDummyFile(envelopeId, fileId)
      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.scanFileCommandTriggered()
      }

      eventually {
        numberOfScansAttempted shouldBe 2
      }
    }

    "retry virus scanning fails if COMMAND READ TIMED OUT occurs up to the maximum attempts" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      setVirusDetected("COMMAND READ TIMED OUT", timesToReturnVirus = 100)

      val result = uploadDummyFile(envelopeId, fileId)
      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.scanFileCommandTriggered()
      }

      eventually {
        numberOfScansAttempted shouldBe 3
      }
    }
  }
}
