package uk.gov.hmrc.fileupload

import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import uk.gov.hmrc.clamav.{VirusDetectedException, VirusScannerFailureException}
import uk.gov.hmrc.fileupload.DomainFixtures.{anyEnvelopeId, anyFileId}
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions, ITTestAppComponentsWithStubbedClamAV}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

class VirusScanFileUploadISpec extends FileActions with EnvelopeActions with Eventually with ITTestAppComponentsWithStubbedClamAV {

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(50, Millis))


  "File upload front-end" should {
    val fileId = anyFileId
    val envelopeId = anyEnvelopeId

    "transfer a file to the back-end if virus scanning succeeds" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      (stubbedClamAVClient.send (_:Array[Byte])(_: ExecutionContext)).when(*, *).returns(Future.successful(()))
      (stubbedClamAVClient.checkForVirus () (_:ExecutionContext)).when(*).returns(cleanScan())

      val result = uploadDummyFile(envelopeId, fileId)

      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.scanFileCommandTriggered()
      }

      (stubbedClamAVClient.send (_:Array[Byte])(_: ExecutionContext)).verify(*, *).once()
      (stubbedClamAVClient.checkForVirus () (_:ExecutionContext)).verify(*).once()
    }

    "not retry virus scanning fails if general error occurs" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      (stubbedClamAVClient.send (_:Array[Byte])(_: ExecutionContext)).when(*, *).returns(Future.successful(()))
      (stubbedClamAVClient.checkForVirus () (_:ExecutionContext)).when(*).returns(virusDetected())

      val result = uploadDummyFile(envelopeId, fileId)

      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.scanFileCommandTriggered()
      }

      (stubbedClamAVClient.send (_:Array[Byte])(_: ExecutionContext)).verify(*, *).once()
      (stubbedClamAVClient.checkForVirus () (_:ExecutionContext)).verify(*).once()
    }

    "retry virus scanning fails if COMMAND READ TIMED OUT occurs until successful" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      (stubbedClamAVClient.send (_:Array[Byte])(_: ExecutionContext)).when(*, *).returns(Future.successful(()))
      (stubbedClamAVClient.checkForVirus () (_:ExecutionContext)).when(*).returns(commandReadTimeout()).noMoreThanOnce()
      (stubbedClamAVClient.checkForVirus () (_:ExecutionContext)).when(*).returns(cleanScan())

      val result = uploadDummyFile(envelopeId, fileId)
      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.scanFileCommandTriggered()
      }
      (stubbedClamAVClient.send (_:Array[Byte])(_: ExecutionContext)).verify(*, *).twice()
      (stubbedClamAVClient.checkForVirus () (_:ExecutionContext)).verify(*).twice()
    }

    "retry virus scanning if COMMAND READ TIMED OUT occurs until virus detected" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      (stubbedClamAVClient.send (_:Array[Byte])(_: ExecutionContext)).when(*, *).returns(Future.successful(()))
      (stubbedClamAVClient.checkForVirus () (_:ExecutionContext)).when(*).returns(commandReadTimeout()).noMoreThanOnce()
      (stubbedClamAVClient.checkForVirus () (_:ExecutionContext)).when(*).returns(virusDetected())

      val result = uploadDummyFile(envelopeId, fileId)
      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.scanFileCommandTriggered()
      }
      (stubbedClamAVClient.send (_:Array[Byte])(_: ExecutionContext)).verify(*, *).twice()
      (stubbedClamAVClient.checkForVirus () (_:ExecutionContext)).verify(*).twice()
    }

    "retry virus scanning if COMMAND READ TIMED OUT occurs until scanner failure" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      (stubbedClamAVClient.send (_:Array[Byte])(_: ExecutionContext)).when(*, *).returns(Future.successful(()))
      (stubbedClamAVClient.checkForVirus () (_:ExecutionContext)).when(*).returns(commandReadTimeout()).noMoreThanOnce()
      (stubbedClamAVClient.checkForVirus () (_:ExecutionContext)).when(*).returns(scannerFailure())

      val result = uploadDummyFile(envelopeId, fileId)
      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.scanFileCommandTriggered()
      }
      (stubbedClamAVClient.send (_:Array[Byte])(_: ExecutionContext)).verify(*, *).twice()
      (stubbedClamAVClient.checkForVirus () (_:ExecutionContext)).verify(*).twice()
    }

    "retry virus scanning fails if COMMAND READ TIMED OUT occurs up to the maximum attempts" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      (stubbedClamAVClient.send (_:Array[Byte])(_: ExecutionContext)).when(*, *).returns(Future.successful(()))
      (stubbedClamAVClient.checkForVirus () (_:ExecutionContext)).when(*).returns(commandReadTimeout())

      val result = uploadDummyFile(envelopeId, fileId)
      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.scanFileCommandTriggered()
      }

      (stubbedClamAVClient.send (_:Array[Byte])(_: ExecutionContext)).verify(*, *).repeat(3)
      (stubbedClamAVClient.checkForVirus () (_:ExecutionContext)).verify(*).repeat(3)
    }
  }

  private def cleanScan(): Future[Try[Boolean]] = Future.successful(Try(true))
  private def virusDetected(): Future[Try[Boolean]] = Future.successful(Failure(new VirusDetectedException("virus")))
  private def commandReadTimeout(): Future[Try[Boolean]] = Future.successful(Failure(new VirusDetectedException("COMMAND READ TIMED OUT")))
  private def scannerFailure(): Future[Try[Boolean]] = Future.successful(Failure(new VirusScannerFailureException("unexpected")))
}
