package uk.gov.hmrc.fileupload.support

import java.util.UUID

import org.scalatest.{BeforeAndAfterEach, FeatureSpec}
import org.scalatestplus.play.OneServerPerSuite
import play.api.test.FakeApplication
import uk.gov.hmrc.mongo.MongoSpecSupport

trait IntegrationSpec extends FeatureSpec with MongoSpecSupport with OneServerPerSuite with FakeFileUploadBackend with BeforeAndAfterEach {

  override lazy val port: Int = 9000

  val nextId = () => UUID.randomUUID().toString

  implicit override lazy val app: FakeApplication =
    FakeApplication(
      additionalConfiguration = Map(
        "auditing.enabled" -> "false",
        "Test.clam.antivirus.runStub" -> "true",
        "microservice.services.file-upload-backend.port" -> backend.port(),
        "mongodb.uri" -> s"mongodb://localhost:27017/$databaseName"
      )
    )

}