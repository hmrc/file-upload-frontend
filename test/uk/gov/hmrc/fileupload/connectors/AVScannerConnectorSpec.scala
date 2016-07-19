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

package uk.gov.hmrc.fileupload.connectors

import java.io.{ByteArrayInputStream, File}

import org.scalatest.BeforeAndAfterEach
import play.api.libs.iteratee.Enumerator
import uk.gov.hmrc.clamav.{VirusDetectedException, VirusScannerFailureException}
import uk.gov.hmrc.fileupload.UploadFixtures._
import uk.gov.hmrc.play.test.UnitSpec

import scala.io.Source._
import scala.util.{Failure, Success}

class AVScannerConnectorSpec extends UnitSpec with BeforeAndAfterEach {
  import scala.concurrent.ExecutionContext.Implicits.global

  "An AV scanner connector" should {
    "take an Array[Byte] enumerator and pass to an external scanner" in {
      val scanner = new TestAvScannerConnector()
      val enumerator = Enumerator.fromStream(new ByteArrayInputStream("test".getBytes))

      await(scanner.scan(enumerator))

      scanner.sentData should be ("test".getBytes)
    }

    "be able to cope with a large quantity of data" in {
      val scanner = new TestAvScannerConnector()
      val enumerator = Enumerator.fromFile(new File("test/resources/768KBFile.txt"))

      await(scanner.scan(enumerator))

      scanner.sentData should be (fromFile(new File("test/resources/768KBFile.txt")).mkString.getBytes)
    }

    "on processing clean data, return a Success(true) response" in {
      val scanner = new TestAvScannerConnector()

      val enumerator = Enumerator.fromFile(new File("test/resources/768KBFile.txt"))

      await(scanner.scan(enumerator)) should be (Success(true))
    }

    "on processing dirty data, return a Failure(_:VirusDetectedException) response" in {
      val failure = Failure(new VirusDetectedException("TEST"))
      val scanner = new TestAvScannerConnector(failure)

      val enumerator = Enumerator.fromFile(new File("test/resources/eicar-standard-av-test-file.txt"))

      val result = await(scanner.scan(enumerator))

      result.isFailure should be (true)
      result should be (failure)
    }

    "on failure to communicate, return a Failure(_:VirusScannerFailureException) response" in {
      val failure = Failure(new VirusScannerFailureException("TEST"))
      val scanner = new TestAvScannerConnector(failure)

      val enumerator = Enumerator.fromFile(new File("test/resources/768KBFile.txt"))

      val result = await(scanner.scan(enumerator))

      result.isFailure should be (true)
      result should be (failure)
    }
  }
}
