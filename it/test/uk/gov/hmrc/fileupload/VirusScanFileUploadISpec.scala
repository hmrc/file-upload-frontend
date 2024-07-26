/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.fileupload

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.{IntegrationPatience, Eventually}
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.clamav.model.ScanningResult
import uk.gov.hmrc.fileupload.DomainFixtures.{anyEnvelopeId, anyFileId}
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions, ITTestAppComponentsWithStubbedClamAV}

import java.io.InputStream
import scala.concurrent.{ExecutionContext, Future}

class VirusScanFileUploadISpec
  extends FileActions
     with EnvelopeActions
     with Eventually
     with ITTestAppComponentsWithStubbedClamAV
     with MockitoSugar
     with IntegrationPatience {

  "File upload front-end" should {
    val fileId = anyFileId
    val envelopeId = anyEnvelopeId

    "transfer a file to the back-end if virus scanning succeeds" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      when(stubbedAvClient.sendAndCheck(any[InputStream], any[Int])(using any[ExecutionContext]))
        .thenReturn(cleanScan())

      val result = uploadDummyFile(envelopeId, fileId)

      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.markFileAsCleanCommandTriggered()
      }

      verify(stubbedAvClient).sendAndCheck(any[InputStream], any[Int])(using any[ExecutionContext])
    }

    "not retry virus scanning fails if general error occurs" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      when(stubbedAvClient.sendAndCheck(any[InputStream], any[Int])(using any[ExecutionContext]))
        .thenReturn(virusDetected())

      val result = uploadDummyFile(envelopeId, fileId)

      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.markFileAsInfectedTriggered()
      }

      verify(stubbedAvClient).sendAndCheck(any[InputStream], any[Int])(using any[ExecutionContext])
    }

    "retry virus scanning fails if COMMAND READ TIMED OUT occurs until successful" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      when(stubbedAvClient.sendAndCheck(any[InputStream], any[Int])(using any[ExecutionContext]))
        .thenReturn(
          commandReadTimeout(),
          cleanScan()
        )

      val result = uploadDummyFile(envelopeId, fileId)
      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.markFileAsCleanCommandTriggered()
      }
      verify(stubbedAvClient, times(2)).sendAndCheck(any[InputStream], any[Int])(using any[ExecutionContext])
    }

    "retry virus scanning if COMMAND READ TIMED OUT occurs until scanner failure" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      when(stubbedAvClient.sendAndCheck(any[InputStream], any[Int])(using any[ExecutionContext]))
        .thenReturn(
          commandReadTimeout(),
          scannerFailure()
        )

      val result = uploadDummyFile(envelopeId, fileId)
      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        verify(stubbedAvClient, times(2)).sendAndCheck(any[InputStream], any[Int])(using any[ExecutionContext])
      }
    }

    "retry virus scanning if COMMAND READ TIMED OUT occurs until virus detected" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      when(stubbedAvClient.sendAndCheck(any[InputStream], any[Int])(using any[ExecutionContext]))
        .thenReturn(
          commandReadTimeout(),
          virusDetected()
        )

      val result = uploadDummyFile(envelopeId, fileId)
      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.markFileAsInfectedTriggered()
      }
      verify(stubbedAvClient, times(2)).sendAndCheck(any[InputStream], any[Int])(using any[ExecutionContext])
    }

    "retry virus scanning fails if COMMAND READ TIMED OUT occurs up to the maximum attempts" in {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)
      when(stubbedAvClient.sendAndCheck(any[InputStream], any[Int])(using any[ExecutionContext]))
        .thenReturn(commandReadTimeout())

      val result = uploadDummyFile(envelopeId, fileId)
      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        verify(stubbedAvClient, times(3)).sendAndCheck(any[InputStream], any[Int])(using any[ExecutionContext])
      }
    }
  }

  private def cleanScan()         : Future[ScanningResult] = Future.successful(ScanningResult.Clean)
  private def virusDetected()     : Future[ScanningResult] = Future.successful(ScanningResult.Infected("virus"))
  private def commandReadTimeout(): Future[ScanningResult] = Future.successful(ScanningResult.Infected("COMMAND READ TIMED OUT"))
  private def scannerFailure()    : Future[ScanningResult] = Future.failed(RuntimeException("unexpected"))
}
