package uk.gov.hmrc.fileupload

import java.io.InputStream

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.{IntegrationPatience, Eventually}
import uk.gov.hmrc.clamav.model.{Clean, Infected, ScanningResult}
import uk.gov.hmrc.fileupload.DomainFixtures.{anyEnvelopeId, anyFileId}
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions, ITTestAppComponentsWithStubbedClamAV}

import scala.concurrent.{ExecutionContext, Future}

class VirusScanFileUploadISpec
  extends FileActions
     with EnvelopeActions
     with Eventually
     with ITTestAppComponentsWithStubbedClamAV
     with IntegrationPatience {

  "File upload front-end" should {
    val fileId = anyFileId
    val envelopeId = anyEnvelopeId

    "transfer a file to the back-end if virus scanning succeeds" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      when(stubbedAvClient.sendAndCheck(any[InputStream], any[Int])(any[ExecutionContext]))
        .thenReturn(cleanScan())

      val result = uploadDummyFile(envelopeId, fileId)

      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.markFileAsCleanCommandTriggered()
      }

      verify(stubbedAvClient).sendAndCheck(any[InputStream], any[Int])(any[ExecutionContext])
    }

    "not retry virus scanning fails if general error occurs" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      when(stubbedAvClient.sendAndCheck(any[InputStream], any[Int])(any[ExecutionContext]))
        .thenReturn(virusDetected())

      val result = uploadDummyFile(envelopeId, fileId)

      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.markFileAsInfectedTriggered()
      }

      verify(stubbedAvClient).sendAndCheck(any[InputStream], any[Int])(any[ExecutionContext])
    }

    "retry virus scanning fails if COMMAND READ TIMED OUT occurs until successful" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      when(stubbedAvClient.sendAndCheck(any[InputStream], any[Int])(any[ExecutionContext]))
        .thenReturn(commandReadTimeout())
        .thenReturn(cleanScan())

      val result = uploadDummyFile(envelopeId, fileId)
      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.markFileAsCleanCommandTriggered()
      }
      verify(stubbedAvClient, times(2)).sendAndCheck(any[InputStream], any[Int])(any[ExecutionContext])
    }

    "retry virus scanning if COMMAND READ TIMED OUT occurs until scanner failure" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      when(stubbedAvClient.sendAndCheck(any[InputStream], any[Int])(any[ExecutionContext]))
        .thenReturn(commandReadTimeout())
        .thenReturn(scannerFailure())

      val result = uploadDummyFile(envelopeId, fileId)
      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        verify(stubbedAvClient, times(2)).sendAndCheck(any[InputStream], any[Int])(any[ExecutionContext])
      }
    }

    "retry virus scanning if COMMAND READ TIMED OUT occurs until virus detected" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      when(stubbedAvClient.sendAndCheck(any[InputStream], any[Int])(any[ExecutionContext]))
        .thenReturn(commandReadTimeout())
        .thenReturn(virusDetected())

      val result = uploadDummyFile(envelopeId, fileId)
      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.markFileAsInfectedTriggered()
      }
      verify(stubbedAvClient, times(2)).sendAndCheck(any[InputStream], any[Int])(any[ExecutionContext])
    }

    "retry virus scanning fails if COMMAND READ TIMED OUT occurs up to the maximum attempts" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      when(stubbedAvClient.sendAndCheck(any[InputStream], any[Int])(any[ExecutionContext]))
        .thenReturn(commandReadTimeout())

      val result = uploadDummyFile(envelopeId, fileId)
      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        verify(stubbedAvClient, times(3)).sendAndCheck(any[InputStream], any[Int])(any[ExecutionContext])
      }
    }
  }

  private def cleanScan()         : Future[ScanningResult] = Future.successful(Clean)
  private def virusDetected()     : Future[ScanningResult] = Future.successful(Infected("virus"))
  private def commandReadTimeout(): Future[ScanningResult] = Future.successful(Infected("COMMAND READ TIMED OUT"))
  private def scannerFailure()    : Future[ScanningResult] = Future.failed(new RuntimeException("unexpected"))
}
