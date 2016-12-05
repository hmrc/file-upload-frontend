package uk.gov.hmrc.fileupload.support

import java.net.ServerSocket
import java.util.UUID

import org.scalatest.FeatureSpec
import org.scalatestplus.play.OneServerPerSuite
import play.api.test.FakeApplication
import uk.gov.hmrc.clamav.fake.FakeClam
import uk.gov.hmrc.fileupload.transfer.FakeFileUploadBackend

import scala.concurrent.ExecutionContext

trait IntegrationSpec extends FeatureSpec with OneServerPerSuite with FakeFileUploadBackend {

  implicit val ecIntegrationSpec = ExecutionContext.global

  override lazy val port: Int = 9000

  val nextId = () => UUID.randomUUID().toString

  lazy val fakeClamSocket = new ServerSocket(0)
  lazy val fakeClam = new FakeClam(fakeClamSocket)

  override def beforeAll(): Unit = {
    super.beforeAll()

    fakeClam.start()
  }

  override def afterAll(): Unit = {
    super.afterAll()

    fakeClam.stop()
  }

  implicit override lazy val app: FakeApplication =
    FakeApplication(
      additionalConfiguration = Map(
        "auditing.enabled" -> "false",
        "Test.clam.antivirus.host" -> "127.0.0.1",
        "Test.clam.antivirus.port" -> fakeClamSocket.getLocalPort,
        "microservice.services.file-upload-backend.port" -> backend.port()
      )
    )
}