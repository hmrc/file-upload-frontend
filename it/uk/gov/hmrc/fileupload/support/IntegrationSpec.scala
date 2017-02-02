package uk.gov.hmrc.fileupload.support

import java.net.ServerSocket
import java.util.UUID

import org.scalatest.{BeforeAndAfterEach, FeatureSpec}
import org.scalatestplus.play.OneServerPerSuite
import play.api.test.FakeApplication
import uk.gov.hmrc.clamav.fake.FakeClam
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.ExecutionContext

trait IntegrationSpec extends FeatureSpec with MongoSpecSupport with OneServerPerSuite with FakeFileUploadBackend with BeforeAndAfterEach {

  override lazy val port: Int = 9000

  val nextId = () => UUID.randomUUID().toString

  // creating and closing a ServerSocket in order to get a free port
  val fakeClamSocket = new ServerSocket(0)
  val socketPort = fakeClamSocket.getLocalPort
  fakeClamSocket.close()

  var fakeClam: FakeClam = _

  override def beforeEach(): Unit = {
    fakeClam = new FakeClam(new ServerSocket(socketPort))(ExecutionContext.global)
    fakeClam.start()
  }

  override def afterEach(): Unit = {
    fakeClam.stop()
  }

  implicit override lazy val app: FakeApplication =
    FakeApplication(
      additionalConfiguration = Map(
        "auditing.enabled" -> "false",
        "Test.clam.antivirus.host" -> "127.0.0.1",
        "Test.clam.antivirus.port" -> socketPort,
        "microservice.services.file-upload-backend.port" -> backend.port(),
        "mongodb.uri" -> s"mongodb://localhost:27017/$databaseName"
      )
    )

}