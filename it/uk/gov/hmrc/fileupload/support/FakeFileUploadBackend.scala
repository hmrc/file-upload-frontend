package uk.gov.hmrc.fileupload.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Suite}
import play.api.http.Status
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}
import scala.collection.JavaConverters._

trait FakeFileUploadBackend extends BeforeAndAfterAll with ScalaFutures {
  this: Suite =>

  lazy val backend = new WireMockServer(wireMockConfig().dynamicPort())
  lazy val backendPort = backend.port()

  final lazy val fileUploadBackendBaseUrl = s"http://localhost:$backendPort"

  override def beforeAll() = {
    super.beforeAll()
    backend.start()
    backend.addStubMapping(
      post(urlPathMatching("/file-upload/events/*"))
        .willReturn(aResponse().withStatus(Status.OK))
        .build())
  }

  override def afterAll() = {
    println("Stopping the mock backend server")
    super.afterAll()
    backend.stop()
  }

  val ENVELOPE_OPEN_RESPONSE = """ { "status" : "OPEN" } """
  val ENVELOPE_CLOSED_RESPONSE = """ { "status" : "CLOSED" } """

  object Wiremock {

    def respondToEnvelopeCheck(envelopeId: EnvelopeId, status: Int = Status.OK, body: String = ENVELOPE_OPEN_RESPONSE) = {
      backend.addStubMapping(
        get(urlPathMatching(s"/file-upload/envelopes/${ envelopeId.value }"))
          .willReturn(
            aResponse()
              .withBody(body)
              .withStatus(status))
          .build())
    }

    def responseToUpload(envelopeId: EnvelopeId, fileId: FileId, status: Int = Status.OK, body: String = "") = {
      backend.addStubMapping(
        put(urlPathMatching(fileContentUrl(envelopeId, fileId)))
          .willReturn(
            aResponse()
              .withBody(body)
              .withStatus(status))
          .build())
    }

    def respondToCreateEnvelope(envelopeIdOfCreated: EnvelopeId) = {
      backend.addStubMapping(
        post(urlPathMatching(s"/file-upload/envelopes"))
          .willReturn(
            aResponse()
              .withHeader("Location", s"$fileUploadBackendBaseUrl/file-upload/envelopes/${ envelopeIdOfCreated.value }")
              .withStatus(Status.CREATED))
          .build())
    }

    def responseToDownloadFile(envelopeId: EnvelopeId, fileId: FileId, textBody: String = "", status: Int = Status.OK) = {
      backend.addStubMapping(
        get(urlPathMatching(fileContentUrl(envelopeId, fileId)))
          .willReturn(
            aResponse()
              .withBody(textBody)
              .withStatus(status))
          .build())
    }

    def uploadedFile(envelopeId: EnvelopeId, fileId: FileId): Option[LoggedRequest] = {
      backend.findAll(putRequestedFor(urlPathMatching(fileContentUrl(envelopeId, fileId)))).asScala.headOption
    }

    def quarantinedEventTriggered() = {
      backend.verify(postRequestedFor(urlEqualTo("/file-upload/events/FileInQuarantineStored")))
    }

    def fileScannedEventTriggered() = {
      backend.verify(postRequestedFor(urlEqualTo("/file-upload/events/FileScanned")))
    }

    private def fileContentUrl(envelopeId: EnvelopeId, fileId: FileId) = {
      s"/file-upload/envelopes/$envelopeId/files/$fileId"
    }

    private def metadataContentUrl(envelopId: EnvelopeId, fileId: FileId) = {
      s"/file-upload/envelopes/$envelopId/files/$fileId/metadata"
    }

  }
}
