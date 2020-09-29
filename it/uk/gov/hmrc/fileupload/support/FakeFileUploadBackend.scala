package uk.gov.hmrc.fileupload.support

import better.files.File
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import io.findify.s3mock.S3Mock
import io.findify.s3mock.request.CreateBucketConfiguration
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, Suite}
import play.api.http.Status
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

trait FakeFileUploadBackend extends BeforeAndAfterAll with ScalaFutures with IntegrationPatience {
  this: Suite =>

  import ExecutionContext.Implicits.global

  lazy val backend = new WireMockServer(wireMockConfig().dynamicPort())
  lazy val backendPort: Int = backend.port()

  lazy val workDir = s"/tmp/s3"
  // create and start S3 API mock
  lazy val s3MockServer = S3Mock(port = 8001, dir = workDir)

  final lazy val fileUploadBackendBaseUrl = s"http://localhost:$backendPort"

  s3MockServer.start
  s3MockServer.p.createBucket("file-upload-quarantine", new CreateBucketConfiguration(locationConstraint=None))
  s3MockServer.p.createBucket("file-upload-transient", new CreateBucketConfiguration(locationConstraint=None))

  backend.start()
  backend.addStubMapping(
    post(
      urlPathMatching("/file-upload/events/*"))
        .willReturn(aResponse().withStatus(Status.OK)
    )
    .build()
  )

  backend.addStubMapping(
    post(
      urlPathMatching("/file-upload/commands/*"))
        .willReturn(aResponse().withStatus(Status.OK)
    )
    .build()
  )

  override def afterAll(): Unit = {
    super.afterAll()
    backend.stop()
    s3MockServer.stop
    File(workDir).delete()
  }

  val ENVELOPE_OPEN_RESPONSE: String =
    """ { "status" : "OPEN",
          "constraints" : {
            "maxItems" : 100,
            "maxSize" : "25MB",
            "maxSizePerItem" : "10MB"}
          } """.stripMargin

  val ENVELOPE_CLOSED_RESPONSE: String =
    """ { "status" : "CLOSED",
          "constraints" : {
            "maxItems" : 100,
            "maxSize" : "25MB",
            "maxSizePerItem" : "10MB"}
          } """.stripMargin

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

    def uploadedFile(envelopeId: EnvelopeId, fileId: FileId): Option[LoggedRequest] =
      backend.findAll(putRequestedFor(urlPathMatching(fileContentUrl(envelopeId, fileId)))).asScala.headOption

    def quarantineFileCommandTriggered() =
      backend.verify(postRequestedFor(urlEqualTo("/file-upload/commands/quarantine-file")))

    def markFileAsCleanCommandTriggered() =
      backend.verify(postRequestedFor(urlEqualTo("/file-upload/commands/mark-file-as-clean")))

    def markFileAsInfectedTriggered() =
      backend.verify(postRequestedFor(urlEqualTo("/file-upload/commands/mark-file-as-infected")))

    private def fileContentUrl(envelopeId: EnvelopeId, fileId: FileId) = {
      s"/file-upload/envelopes/$envelopeId/files/$fileId"
    }

    private def metadataContentUrl(envelopId: EnvelopeId, fileId: FileId) = {
      s"/file-upload/envelopes/$envelopId/files/$fileId/metadata"
    }

  }
}
