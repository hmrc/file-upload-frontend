package uk.gov.hmrc.fileupload

import org.scalatest.Matchers
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import play.api.http.MimeTypes
import play.api.libs.ws.{WS, WSResponse}
import play.api.test.FakeRequest
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions, IntegrationSpec}

class ShowErrorAsJsonISpec extends IntegrationSpec with Matchers {

  feature("File upload front-end") {

    val testEnvelopeId = EnvelopeId()
    val testFileId = FileId()


    scenario("when errors 400 Bad Request occur, message should show as Json") {

      responseToUpload(testEnvelopeId, testFileId)
      respondToEnvelopeCheck(testEnvelopeId)

      val result = uploadNilFile(testEnvelopeId, testFileId)

      result.status shouldBe 400
      val contentType = result.header("Content-Type").get.split(";")

      contentType(0) shouldBe MimeTypes.JSON

    }

    scenario("when errors 403 Forbidden occur, message should show as Json") {

      val result = uploadWhenForbidden(testEnvelopeId, testFileId)

      result.status shouldBe 403

      val contentType = result.header("Content-Type").get.split(";")

      contentType(0) shouldBe MimeTypes.TEXT

    }

    scenario("when errors 404 Not Found occur, message should show as Json") {

      val result = uploadDummyFile(EnvelopeId(), FileId())

      result.status shouldBe 404

      val contentType = result.header("Content-Type").get.split(";")

      contentType(0) shouldBe MimeTypes.JSON

    }


    scenario("when errors 413 Entity To Large occur, message should show as Json") {

      respondToEnvelopeCheck(testEnvelopeId)

      val result = uploadTooLargeFile(testEnvelopeId, testFileId)

      result.status shouldBe 413

    }

    scenario("when errors 423 Envelope Closed/LOCKED occur, message should show as Json") {

      respondToEnvelopeCheck(testEnvelopeId, body = ENVELOPE_CLOSED_RESPONSE)

      val result = uploadDummyFile(testEnvelopeId, testFileId)

      result.status shouldBe 423

      val contentType = result.header("Content-Type").get.split(";")

      contentType(0) shouldBe MimeTypes.JSON

    }


    scenario("when errors 500 message should show as Json") {

      respondWithFail(testEnvelopeId, body = ENVELOPE_CLOSED_RESPONSE)

      val result = uploadDummyFile(testEnvelopeId, testFileId)

      result.status shouldBe 500

      val contentType = result.header("Content-Type").get.split(";")

      contentType(0) shouldBe MimeTypes.JSON
    }


  }

  def uploadDummyFile(envelopeId: EnvelopeId, fileId: FileId): WSResponse = {
    WS.url(s"http://localhost:$port/file-upload/upload/envelopes/$envelopeId/files/$fileId")
      .withHeaders("Content-Type" -> "multipart/form-data; boundary=---011000010111000001101001",
        "X-Request-ID" -> "someId",
        "X-Session-ID" -> "someId",
        "X-Requested-With" -> "someId")
      .post("-----011000010111000001101001\r\nContent-Disposition: form-data; name=\"file1\"; filename=\"test.txt\"\r\nContent-Type: text/plain\r\n\r\nsomeTextContents\r\n-----011000010111000001101001--")
      .futureValue(PatienceConfig(timeout = Span(10, Seconds)))
  }

  def uploadWhenForbidden(envelopeId: EnvelopeId, fileId: FileId): WSResponse = {
    WS.url(s"http://localhost:$port/file-upload/upload/envelopes/$envelopeId/files/$fileId")
      .withHeaders("Content-Type" -> "multipart/form-data; boundary=---011000010111000001101001")
      .post("")
      .futureValue(PatienceConfig(timeout = Span(10, Seconds)))
  }

  def uploadNilFile(envelopeId: EnvelopeId, fileId: FileId): WSResponse = {
    WS.url(s"http://localhost:$port/file-upload/upload/envelopes/$envelopeId/files/$fileId")
      .withHeaders("Content-Type" -> "multipart/form-data; boundary=---011000010111000001101001",
        "X-Request-ID" -> "someId",
        "X-Session-ID" -> "someId",
        "X-Requested-With" -> "someId")
      .post("")
      .futureValue(PatienceConfig(timeout = Span(10, Seconds)))
  }

  def uploadTooLargeFile(envelopeId: EnvelopeId, fileId: FileId): WSResponse = {
    WS.url(s"http://localhost:$port/file-upload/upload/envelopes/$envelopeId/files/$fileId")
      .withHeaders("Content-Type" -> "",
        "X-Request-ID" -> "someId",
        "X-Session-ID" -> "someId",
        "X-Requested-With" -> "someId")
      .post("")
      .futureValue(PatienceConfig(timeout = Span(10, Seconds)))
  }

  def uploadToClosedEnvelope(envelopeId: EnvelopeId, fileId: FileId): WSResponse = {
    WS.url(s"http://localhost:$port/file-upload/upload/envelopes/$envelopeId/files/$fileId")
      .withHeaders("Content-Type" -> "multipart/form-data; boundary=---011000010111000001101001",
        "X-Request-ID" -> "someId",
        "X-Session-ID" -> "someId",
        "X-Requested-With" -> "someId")
      .post("")
      .futureValue(PatienceConfig(timeout = Span(10, Seconds)))
  }

}
