/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.fileupload.support

import better.files.File
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, post, postRequestedFor, put, putRequestedFor, urlPathMatching, urlEqualTo}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import io.findify.s3mock.S3Mock
import io.findify.s3mock.request.CreateBucketConfiguration
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.http.Status
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

import scala.collection.JavaConverters._

trait FakeFileUploadBackend
  extends BeforeAndAfterAll
     with BeforeAndAfterEach
     with ScalaFutures
     with IntegrationPatience {
  this: Suite =>

  lazy val backend = new WireMockServer(wireMockConfig().dynamicPort())
  lazy val backendPort: Int = backend.port()

  // create and start S3 API mock
  lazy val s3Port = 8001
  lazy val workDir = s"/tmp/s3"
  lazy val s3MockServer = S3Mock(port = s3Port, dir = workDir)

  final lazy val fileUploadBackendBaseUrl = s"http://localhost:$backendPort"

  s3MockServer.start
  s3MockServer.p.createBucket("file-upload-quarantine", new CreateBucketConfiguration(locationConstraint = None))
  s3MockServer.p.createBucket("file-upload-transient", new CreateBucketConfiguration(locationConstraint = None))

  backend.start()

  override def afterAll(): Unit = {
    super.afterAll()
    backend.stop()
    s3MockServer.stop
    File(workDir).delete()
  }

  override def beforeEach(): Unit = {
    backend.resetAll()
    backend.stubFor(
      post(
        urlPathMatching("/file-upload/events/(.*)"))
          .willReturn(aResponse().withStatus(Status.OK)
      )
    )

    backend.stubFor(
      post(
        urlPathMatching("/file-upload/commands/(.*)"))
          .willReturn(aResponse().withStatus(Status.OK)
      )
    )
    super.beforeEach()
  }

  val ENVELOPE_OPEN_RESPONSE: String =
    """ { "status" : "OPEN",
          "constraints" : {
            "maxItems" : 100,
            "maxSize" : "25MB",
            "maxSizePerItem" : "10MB"}
          } """

  val ENVELOPE_CLOSED_RESPONSE: String =
    """ { "status" : "CLOSED",
          "constraints" : {
            "maxItems" : 100,
            "maxSize" : "25MB",
            "maxSizePerItem" : "10MB"}
          } """

  object Wiremock {

    def respondToEnvelopeCheck(envelopeId: EnvelopeId, status: Int = Status.OK, body: String = ENVELOPE_OPEN_RESPONSE) =
      backend.stubFor(
        get(urlPathMatching(s"/file-upload/envelopes/${envelopeId.value}"))
          .willReturn(
            aResponse()
              .withBody(body)
              .withStatus(status)
          )
      )

    def responseToUpload(envelopeId: EnvelopeId, fileId: FileId, status: Int = Status.OK, body: String = "") =
      backend.stubFor(
        put(urlPathMatching(fileContentUrl(envelopeId, fileId)))
          .willReturn(
            aResponse()
              .withBody(body)
              .withStatus(status)
          )
      )

    def respondToCreateEnvelope(envelopeIdOfCreated: EnvelopeId) =
      backend.stubFor(
        post(urlPathMatching(s"/file-upload/envelopes"))
          .willReturn(
            aResponse()
              .withHeader("Location", s"$fileUploadBackendBaseUrl/file-upload/envelopes/${envelopeIdOfCreated.value}")
              .withStatus(Status.CREATED)
          )
      )

    def responseToDownloadFile(envelopeId: EnvelopeId, fileId: FileId, textBody: String = "", status: Int = Status.OK) =
      backend.stubFor(
        get(urlPathMatching(fileContentUrl(envelopeId, fileId)))
          .willReturn(
            aResponse()
              .withBody(textBody)
              .withStatus(status)
          )
      )

    def uploadedFile(envelopeId: EnvelopeId, fileId: FileId): Option[LoggedRequest] =
      backend.findAll(putRequestedFor(urlPathMatching(fileContentUrl(envelopeId, fileId)))).asScala.headOption

    def quarantineFileCommandTriggered() =
      backend.verify(postRequestedFor(urlEqualTo("/file-upload/commands/quarantine-file")))

    def markFileAsCleanCommandTriggered() =
      backend.verify(postRequestedFor(urlEqualTo("/file-upload/commands/mark-file-as-clean")))

    def markFileAsInfectedTriggered() =
      backend.verify(postRequestedFor(urlEqualTo("/file-upload/commands/mark-file-as-infected")))

    private def fileContentUrl(envelopeId: EnvelopeId, fileId: FileId) =
      s"/file-upload/envelopes/$envelopeId/files/$fileId"
  }
}
