package uk.gov.hmrc.fileupload.support

import java.util.UUID

import org.scalatest.FeatureSpec
import org.scalatestplus.play.OneServerPerSuite
import play.api.test.FakeApplication
import uk.gov.hmrc.fileupload.transfer.FakeFileUploadBackend

trait IntegrationSpec extends FeatureSpec with OneServerPerSuite with FakeFileUploadBackend {

  override lazy val port: Int = 9000

  val nextId = () => UUID.randomUUID().toString

  implicit override lazy val app: FakeApplication =
    FakeApplication(
      additionalConfiguration = Map(
        "auditing.enabled" -> "false",
        "microservice.services.file-upload-backend.port" -> backend.port()
      )
    )
}