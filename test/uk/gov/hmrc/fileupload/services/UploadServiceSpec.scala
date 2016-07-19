/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.services

import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.fileupload.connectors._
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import org.scalatest.concurrent.Eventually._
import org.scalatest.time.SpanSugar._
import uk.gov.hmrc.clamav.VirusDetectedException

import scala.util.Failure

class UploadServiceSpec extends UnitSpec with OneAppPerSuite with BeforeAndAfterEach {
  import uk.gov.hmrc.fileupload.UploadFixtures._

  private val quarantineStoreConnector = TmpFileQuarantineStoreConnector

  def testUploadService(avScannerConnector: AvScannerConnector = new TestAvScannerConnector()) =
    new UploadService(avScannerConnector, TestFileUploadConnector, quarantineStoreConnector)

  implicit val hc = HeaderCarrier()

  "An upload service" should {
    "Flag files as 'Scanning' when they have been passed to the AV scanner" ignore {
      val data = FileData(data = testFile, name = "TEST.out", contentType = "text/plain", envelopeId = validEnvelopeId, fileId = "1")

      val uploadService = testUploadService()

      await(uploadService.validateAndPersist(data))

      await(quarantineStoreConnector.list(Unscanned)).length should be (1)

      uploadService.scanUnscannedFiles()

      eventually(timeout(4 seconds)) { await(quarantineStoreConnector.list(Unscanned)).length should be (0) }

      await(quarantineStoreConnector.list(Scanning)).length should be (1)
    }

    "Flag files as 'Clean' when they have passed AV scanning" ignore {
      val data = FileData(data = testFile, name = "TEST.out", contentType = "text/plain", envelopeId = validEnvelopeId, fileId = "1")

      val uploadService = testUploadService()

      await(uploadService.validateAndPersist(data))

      await(quarantineStoreConnector.list(Unscanned)).length should be (1)

      uploadService.scanUnscannedFiles()

      eventually(timeout(4 seconds)) { await(quarantineStoreConnector.list(Unscanned)).length should be (0) }

      eventually(timeout(4 seconds)) { await(quarantineStoreConnector.list(Clean)).length should be (1) }
    }

    "Flag files as 'VirusDetected' when they have failed AV scanning" ignore {
      val data = FileData(data = testFile, name = eicarFile, contentType = "text/plain", envelopeId = validEnvelopeId, fileId = "1")

      val uploadService = testUploadService(new TestAvScannerConnector(Failure(new VirusDetectedException("TEST"))))

      await(uploadService.validateAndPersist(data))

      await(quarantineStoreConnector.list(Unscanned)).length should be (1)

      uploadService.scanUnscannedFiles()

      eventually(timeout(4 seconds)) { await(quarantineStoreConnector.list(Unscanned)).length should be (0) }

      eventually(timeout(4 seconds)) { await(quarantineStoreConnector.list(VirusDetected)).length should be (1) }
    }
  }

  override protected def beforeEach() { cleanTmp() }
  override protected def afterEach() { cleanTmp() }
}
