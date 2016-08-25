package uk.gov.hmrc.fileupload

import play.api.libs.Files
import play.api.libs.Files.TemporaryFile
import play.api.libs.ws.WS
import play.api.mvc.MultipartFormData
import play.api.mvc.MultipartFormData.{BadPart, FilePart, MissingFilePart}
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.fileupload.DomainFixtures._
import uk.gov.hmrc.fileupload.fileupload.JSONReadFile
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions, IntegrationSpec}
import uk.gov.hmrc.fileupload.transfer.{FakeAuditer, FakeFileUploadBackend}

import scala.concurrent.Future

/**
  * Integration tests for FILE-100
  * Update FileMetadata
  *
  */
class FileUploadISpec extends IntegrationSpec with FileActions with EnvelopeActions with FakeFileUploadBackend with FakeAuditer {

  feature("File upload front-end") {
    ignore("transfer a file to the back-end") {
      val fileContents = "someTextContents"
      val tempFile = temporaryTexFile(Some(fileContents))
      val file = anyFileFor(file = tempFile)

      responseToUpload(file.envelopeId, file.fileId)
      stubResponseForSendMetadata(file.envelopeId, file.fileId)
      respondToEnvelopeCheck(file.envelopeId)

      val files = Seq[FilePart[TemporaryFile]](FilePart("file1", tempFile.getName, Some("text/plain"), TemporaryFile(tempFile)))
      val multipartBody = MultipartFormData(Map[String, Seq[String]](), files, Seq[BadPart](), Seq[MissingFilePart]())
      val fakeRequest = FakeRequest[MultipartFormData[Files.TemporaryFile]]("POST",
        s"/file-upload/upload/envelope/${file.envelopeId.value}/file/${file.fileId.value}", FakeHeaders(), multipartBody)

      val uploadRequest: FakeRequest[MultipartFormData[Future[JSONReadFile]]] = RestFixtures.validUploadRequest(file)

      uploadedFile(file.envelopeId, file.fileId).map(_.getBodyAsString) shouldBe Some(fileContents)

      eventTriggered()
    }
  }
}

