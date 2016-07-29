package uk.gov.hmrc.fileupload.testonly

import java.net.URL

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Second, Span}
import play.api.http.Status
import play.api.test.FakeRequest
import uk.gov.hmrc.fileupload.DomainFixtures._
import uk.gov.hmrc.fileupload.transfer.FakeFileUploadBackend
import uk.gov.hmrc.fileupload.{FrontendGlobal, ServiceConfig}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class DownloadFileEndpointISpec extends UnitSpec with ScalaFutures with WithFakeApplication with FakeFileUploadBackend {

  override lazy val fileUploadBackendPort = new URL(ServiceConfig.fileUploadBackendBaseUrl).getPort

  lazy val controller = FrontendGlobal.getControllerInstance[TestOnlyController](classOf[TestOnlyController])

  "Downloading a file" should {
    "delegate to the backend" in {
      val envelopeId = anyEnvelopeId
      val fileId = anyFileId
      val body = "fileBody"

      val request = FakeRequest("GET", s"/test-only/download-file/envelope/${envelopeId.value}/file/${fileId.value}/content")
      responseToDownloadFile(envelopeId, fileId, body)

      val response = controller.downloadFile(envelopeId.value, fileId.value)(request).futureValue

      status(response) shouldBe Status.OK
      bodyOf(response) shouldBe body
    }
  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(1, Second))
}
