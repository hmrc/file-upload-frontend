package uk.gov.hmrc.fileupload

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Second, Span}
import uk.gov.hmrc.fileupload.DomainFixtures._
import uk.gov.hmrc.fileupload.RestFixtures.validUploadRequest
import uk.gov.hmrc.fileupload.controllers.FileUploadController
import uk.gov.hmrc.fileupload.transfer.FakeFileUploadBackend
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class FileUploadISpec extends UnitSpec with ScalaFutures with WithFakeApplication with FakeFileUploadBackend {

  import scala.concurrent.ExecutionContext.Implicits.global

  override lazy val fileUploadBackendPort = ServiceConfig.getConfInt("file-upload-backend.port", fail("Cannot get port property"))

  val controller = FrontendGlobal.getControllerInstance[FileUploadController](classOf[FileUploadController])

  "File upload front-end" should {
    "transfer a file to the back-end" in {
      val fileContents = "someTextContents"
      val tempFile = temporaryTexFile(Some(fileContents))
      val file = anyFileFor(file = tempFile)
      val request = validUploadRequest(file)
      responseToUpload(file.envelopeId, file.fileId)
      respondToEnvelopeCheck(file.envelopeId)

      controller.upload()(request).futureValue

      uploadedFile(file.envelopeId, file.fileId).map(_.getBodyAsString) shouldBe Some(fileContents)
    }
  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(1, Second))
}
