package uk.gov.hmrc.fileupload.support

import org.scalatest.Suite
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

trait FileActions extends ActionsSupport {
  this: Suite =>

  val file: String = "-----011000010111000001101001\r\n" +
                     "Content-Disposition: form-data; name=\"file1\"; filename=\"test.pdf\"\r\n" +
                     "Content-Type: application/pdf\r\n\r\n" +
                     "someTextContents\r\n-----011000010111000001101001--"

  val tooLargeFile: String = "-----011000010111000001101001\r\n" +
                             "Content-Disposition: form-data; name=\"file1\"; filename=\"test.pdf\"\r\n" +
                             "Content-Type: application/pdf\r\n\r\n" +
                             s"${"someTextContent" * 1024 * 1024}\r\n-----011000010111000001101001--"

  val wrongTypeFile: String = "-----011000010111000001101001\r\n" +
                              "Content-Disposition: form-data; name=\"file1\"; filename=\"test.txt\"\r\n" +
                              "Content-Type: text/plain\r\n\r\n" +
                              "someTextContents\r\n-----011000010111000001101001--"

  def upload(data: Array[Byte], envelopeId: EnvelopeId, fileId: FileId): WSResponse =
    client
      .url(s"$url/envelopes/$envelopeId/files/$fileId/content")
      .withHeaders("Content-Type" -> "application/octet-stream")
      .put(data)
      .futureValue

  def download(envelopeId: EnvelopeId, fileId: FileId): WSResponse =
    client
      .url(s"$internalUrl/download/envelopes/$envelopeId/files/$fileId")
      .get()
      .futureValue

  def updateFileMetadata(data: String, envelopeId: EnvelopeId, fileId: FileId): WSResponse =
    client
      .url(s"$url/envelopes/$envelopeId/files/$fileId/metadata")
      .withHeaders("Content-Type" -> "application/json")
      .put(data.getBytes)
      .futureValue

  def getFileMetadataFor(envelopeId: EnvelopeId, fileId: FileId): WSResponse =
    client
      .url(s"$url/envelopes/$envelopeId/files/$fileId/metadata")
      .get()
      .futureValue

  def uploadDummyFile(envelopeId: EnvelopeId, fileId: FileId): WSResponse = {
    client.url(s"$url/upload/envelopes/$envelopeId/files/$fileId")
      .withHeaders("Content-Type" -> "multipart/form-data; boundary=---011000010111000001101001",
                   "X-Request-ID" -> "someId",
                   "X-Session-ID" -> "someId",
                   "X-Requested-With" -> "someId")
      .post(file)
      .futureValue
  }

  def uploadDummyFileWithoutRedirects(envelopeId: EnvelopeId, fileId: FileId, redirectParams: String): WSResponse = {
    client.url(s"$url/upload/envelopes/$envelopeId/files/$fileId?$redirectParams")
      .withFollowRedirects(false)
      .withHeaders("Content-Type" -> "multipart/form-data; boundary=---011000010111000001101001",
                   "X-Request-ID" -> "someId",
                   "X-Session-ID" -> "someId",
                   "X-Requested-With" -> "someId")
      .post(file)
      .futureValue
  }

  def uploadDummyLargeFile(envelopeId: EnvelopeId, fileId: FileId): WSResponse = {
    client.url(s"$url/upload/envelopes/$envelopeId/files/$fileId")
      .withHeaders("Content-Type" -> "multipart/form-data; boundary=---011000010111000001101001",
                   "X-Request-ID" -> "someId",
                   "X-Session-ID" -> "someId",
                   "X-Requested-With" -> "someId")
      .post(tooLargeFile)
      .futureValue
  }

  def uploadDummyUnsupportedContentTypeFile(envelopeId: EnvelopeId, fileId: FileId): WSResponse = {
    client.url(s"$url/upload/envelopes/$envelopeId/files/$fileId")
      .withHeaders("Content-Type" -> "multipart/form-data; boundary=---011000010111000001101001",
                   "X-Request-ID" -> "someId",
                   "X-Session-ID" -> "someId",
                   "X-Requested-With" -> "someId")
      .post(wrongTypeFile)
      .futureValue
  }
}
