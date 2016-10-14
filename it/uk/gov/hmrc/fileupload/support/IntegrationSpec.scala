package uk.gov.hmrc.fileupload.support

import java.net.InetSocketAddress
import java.util.UUID

import org.scalatest.FeatureSpec
import org.scalatestplus.play.OneServerPerSuite
import play.api.test.FakeApplication
import uk.gov.hmrc.fileupload.FakeClam
import uk.gov.hmrc.fileupload.transfer.FakeFileUploadBackend

trait IntegrationSpec extends FeatureSpec with OneServerPerSuite with FakeFileUploadBackend {

  override lazy val port: Int = 9000

  val nextId = () => UUID.randomUUID().toString

  lazy private val clamPort = FakeClam.connect().fold(error => fail("Fake clam error", error.cause), _.getLocalPort)

  implicit override lazy val app: FakeApplication =
    FakeApplication(
      additionalConfiguration = Map(
        "auditing.enabled" -> "false",
        "Test.clam.antivirus.host" -> "127.0.0.1",
        "Test.clam.antivirus.port" -> clamPort,
        "microservice.services.file-upload-backend.port" -> backend.port()
      )
    )
}