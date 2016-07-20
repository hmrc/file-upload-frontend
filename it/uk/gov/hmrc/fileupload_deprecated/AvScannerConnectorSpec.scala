package uk.gov.hmrc.fileupload_deprecated

import java.io.File

import org.scalatestplus.play.OneServerPerSuite
import play.api.libs.iteratee.Enumerator
import uk.gov.hmrc.clamav.VirusDetectedException
import uk.gov.hmrc.fileupload_deprecated.connectors.ClamAvScannerConnector
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.util.{Failure, Success}

class AvScannerConnectorSpec extends UnitSpec with WithFakeApplication with OneServerPerSuite {
  import scala.concurrent.ExecutionContext.Implicits.global

  "A clam connector" should {
    "Return a success response for a clean file" ignore {
      await(new ClamAvScannerConnector(ClamAvScannerConnector.virusChecker).scan(Enumerator.fromFile(new File("test/resources/testUpload.txt")))) should be (Success(true))
    }

    "Return a fail response for an infected file" ignore {
      await(new ClamAvScannerConnector(ClamAvScannerConnector.virusChecker).scan(Enumerator.fromFile(new File("test/resources/eicar-standard-av-test-file.txt")))) shouldBe Failure(_:VirusDetectedException)
    }

    "Return a success response for a longer file" ignore {
      await(new ClamAvScannerConnector(ClamAvScannerConnector.virusChecker).scan(Enumerator.fromFile(new File("test/resources/768KBFile.txt")))) should be (Success(true))
    }
  }
}
