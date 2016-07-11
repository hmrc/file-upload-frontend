package uk.gov.hmrc.fileupload

import java.io.File

import org.scalatestplus.play.OneServerPerSuite
import play.api.libs.iteratee.Enumerator
import uk.gov.hmrc.clamav.VirusDetectedException
import uk.gov.hmrc.fileupload.connectors.ClamAvScannerConnector
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.util.{Failure, Success}

class AvScannerConnectorISpec extends UnitSpec with WithFakeApplication with OneServerPerSuite {
  import scala.concurrent.ExecutionContext.Implicits.global

  "A clam connector" should {
    "Return a success response for a clean file" in new ClamAvScannerConnector {
      await(scan(Enumerator.fromFile(new File("test/resources/testUpload.txt")))) shouldBe Success(true)
    }

    "Return a fail response for an infected file" in new ClamAvScannerConnector {
      await(scan(Enumerator.fromFile(new File("test/resources/eicar-standard-av-test-file.txt")))) shouldBe Failure(_:VirusDetectedException)
    }

    "Return a success response for a longer file" in new ClamAvScannerConnector {
      await(scan(Enumerator.fromFile(new File("test/resources/768KBFile.txt")))) shouldBe Success(true)
    }
  }
}
