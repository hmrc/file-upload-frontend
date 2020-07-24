package uk.gov.hmrc.fileupload

import java.io.InputStream

import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import uk.gov.hmrc.clamav.model.{Clean, Infected, ScanningResult}
import uk.gov.hmrc.fileupload.DomainFixtures.{anyEnvelopeId, anyFileId}
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions, ITTestAppComponentsWithStubbedClamAV}

import scala.concurrent.{ExecutionContext, Future}

class VirusScanFileUploadISpec
  extends FileActions
     with EnvelopeActions
     with Eventually
     with ITTestAppComponentsWithStubbedClamAV {

  implicit val defaultPatience: PatienceConfig =
    PatienceConfig(timeout = Span(5, Seconds), interval = Span(50, Millis))

  "File upload front-end" should {
    val fileId = anyFileId
    val envelopeId = anyEnvelopeId

    "transfer a file to the back-end if virus scanning succeeds" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      (stubbedAvClient.sendAndCheck(_: InputStream, _: Int)(_: ExecutionContext)).when(*, *, *).returns(cleanScan())

      val result = uploadDummyFile(envelopeId, fileId)

      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.scanFileCommandTriggered()
      }

      (stubbedAvClient.sendAndCheck(_: InputStream, _: Int)(_: ExecutionContext)).verify(*, *, *).once()
    }

    "not retry virus scanning fails if general error occurs" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      (stubbedAvClient.sendAndCheck(_: InputStream, _: Int)(_: ExecutionContext)).when(*, *, *).returns(virusDetected())

      val result = uploadDummyFile(envelopeId, fileId)

      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.scanFileCommandTriggered()
      }

      (stubbedAvClient.sendAndCheck(_: InputStream, _: Int)(_: ExecutionContext)).verify(*, *, *).once()
    }

    "retry virus scanning fails if COMMAND READ TIMED OUT occurs until successful" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      (stubbedAvClient.sendAndCheck(_: InputStream, _: Int)(_: ExecutionContext)).when(*, *, *).returns(commandReadTimeout()).noMoreThanOnce()
      (stubbedAvClient.sendAndCheck(_: InputStream, _: Int)(_: ExecutionContext)).when(*, *, *).returns(cleanScan())

      val result = uploadDummyFile(envelopeId, fileId)
      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.scanFileCommandTriggered()
      }
      (stubbedAvClient.sendAndCheck(_: InputStream, _: Int)(_: ExecutionContext)).verify(*, *, *).twice()
    }

    "retry virus scanning if COMMAND READ TIMED OUT occurs until scanner failure" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      (stubbedAvClient.sendAndCheck(_: InputStream, _: Int)(_: ExecutionContext)).when(*, *, *).returns(commandReadTimeout()).noMoreThanOnce()
      (stubbedAvClient.sendAndCheck(_: InputStream, _: Int)(_: ExecutionContext)).when(*, *, *).returns(scannerFailure())

      val result = uploadDummyFile(envelopeId, fileId)
      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.scanFileCommandTriggered()
      }
      (stubbedAvClient.sendAndCheck(_: InputStream, _: Int)(_: ExecutionContext)).verify(*, *, *).twice()
    }

    "retry virus scanning fails if COMMAND READ TIMED OUT occurs up to the maximum attempts" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      (stubbedAvClient.sendAndCheck(_: InputStream, _: Int)(_: ExecutionContext)).when(*, *, *).returns(commandReadTimeout())
      val result = uploadDummyFile(envelopeId, fileId)
      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.scanFileCommandTriggered()
      }

      (stubbedAvClient.sendAndCheck(_: InputStream, _: Int)(_: ExecutionContext)).verify(*, *, *).repeat(3)
    }
  }

  private def cleanScan()         : Future[ScanningResult] = Future.successful(Clean)
  private def virusDetected()     : Future[ScanningResult] = Future.successful(Infected("virus"))
  private def commandReadTimeout(): Future[ScanningResult] = Future.successful(Infected("COMMAND READ TIMED OUT"))
  private def scannerFailure()    : Future[ScanningResult] = Future.failed(new RuntimeException("unexpected"))
}
