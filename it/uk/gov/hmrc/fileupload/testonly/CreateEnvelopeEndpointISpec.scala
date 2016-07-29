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

class CreateEnvelopeEndpointISpec extends UnitSpec with ScalaFutures with WithFakeApplication with FakeFileUploadBackend {

  override lazy val fileUploadBackendPort = new URL(ServiceConfig.fileUploadBackendBaseUrl).getPort

  lazy val controller = FrontendGlobal.getControllerInstance[TestOnlyController](classOf[TestOnlyController])

  "Create envelope" should {
    "delegate to the backend" in {
      val request = FakeRequest("POST", "/test-only/create-envelope")
      val envelopeId = anyEnvelopeId
      respondToCreateEnvelope(envelopeId)

      val response = controller.createEnvelope()(request).futureValue

      status(response) shouldBe Status.CREATED
      bodyOf(response) shouldBe s"""{"envelopeId":"${envelopeId.value}"}"""
    }
  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(1, Second))
}
