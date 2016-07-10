package uk.gov.hmrc.fileupload

import java.io.File

import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.OneServerPerSuite
import play.api.Play
import play.api.libs.iteratee.Enumerator
import uk.gov.hmrc.clamav.{ClamAntiVirus, VirusDetectedException}
import uk.gov.hmrc.clamav.config.ClamAvConfig
import uk.gov.hmrc.fileupload.connectors.AvScannerConnector
import uk.gov.hmrc.play.config.RunMode
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.util.{Failure, Success}

class AvScannerConnectorISpec extends UnitSpec with WithFakeApplication with BeforeAndAfterEach with OneServerPerSuite with RunMode {
  import scala.concurrent.ExecutionContext.Implicits.global

  implicit lazy val clamAvConfig = ClamAvConfig(Play.current.configuration.getConfig(s"$env.clam.antivirus"))

  class TestAvScannerConnector extends AvScannerConnector {
    override def virusChecker = {
      ClamAntiVirus(clamAvConfig)
    }
  }

  "A clam connector" should {
    "Return a success response for a clean file" in new TestAvScannerConnector {
      await(scan(Enumerator.fromFile(new File("test/resources/testUpload.txt")))) shouldBe Success(true)
    }

    "Return a fail response for an infected file" in new TestAvScannerConnector {
      await(scan(Enumerator.fromFile(new File("test/resources/eicar-standard-av-test-file.txt")))) shouldBe Failure(_:VirusDetectedException)
    }

    "Return a success response for a longer file" in new TestAvScannerConnector {
      await(scan(Enumerator.fromFile(new File("test/resources/768KBFile.txt")))) shouldBe Success(true)
    }
  }
}
