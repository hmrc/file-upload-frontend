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

import software.amazon.awssdk.services.s3.model.{CopyObjectResponse, PutObjectResponse, S3Object}
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, post, postRequestedFor, put, putRequestedFor, urlPathMatching, urlEqualTo}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import com.typesafe.config.ConfigFactory
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.{IOResult, Materializer}
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.http.Status
import play.api.libs.json.JsValue
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}
import uk.gov.hmrc.fileupload.s3._
import uk.gov.hmrc.fileupload.quarantine.FileData

import java.io.{ByteArrayInputStream, InputStream}
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.Try

trait FakeFileUploadBackend
  extends BeforeAndAfterAll
     with BeforeAndAfterEach
     with ScalaFutures
     with IntegrationPatience {
  this: Suite =>

  lazy val backend = WireMockServer(wireMockConfig().dynamicPort())
  lazy val backendPort: Int = backend.port()
  final lazy val fileUploadBackendBaseUrl = s"http://localhost:$backendPort"

  private val awsConfig: AwsConfig =
    AwsConfig(ConfigFactory.load()) // should get this from Guice (IntegrationTestApplicationComponents)

  val s3Service = InMemoryS3Service(awsConfig)
  s3Service.initBucket(awsConfig.quarantineBucketName)
  s3Service.initBucket(awsConfig.transientBucketName)

  backend.start()

  override def afterAll(): Unit = {
    super.afterAll()
    backend.stop()
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
  def deleteQuarantineBucket(): Unit =
    s3Service.deleteBucket(awsConfig.quarantineBucketName)

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

case class S3File(content: String, fileSize: Int) {
  def toStreamWithMetadata: StreamWithMetadata =
    StreamWithMetadata(
      stream   = Source.single(ByteString(content.getBytes("UTF-8")))
                   .mapMaterializedValue {
                     _ => Future.successful(IOResult(0))
                   },
      metadata = Metadata(
        contentType   = "binary/octet-stream",
        contentLength = fileSize,
        versionId     = "",
        ETag          = "",
        s3Metadata    = None
      )
    )

  def toFileData: FileData =
    FileData(
      length      = fileSize,
      filename    = "",
      contentType = None,
      data        = ByteArrayInputStream(content.getBytes("UTF-8"))
    )
}

class InMemoryS3Service(
  override val awsConfig: AwsConfig
) extends S3Service {
  private val buckets = AtomicReference(Map[String, Map[String, S3File]]())

  def initBucket(bucket: String): Unit =
    buckets.updateAndGet(_ + (bucket -> Map[String, S3File]()))

  def deleteBucket(bucketName: String): Unit =
    buckets.updateAndGet(_ - bucketName)

  private def getBucket(bucketName: String): Map[String, S3File] =
    buckets.get().get(bucketName).getOrElse(sys.error(s"Bucket $bucketName does not exist"))

  private def updateBucket(bucketName: String)(f: Map[String, S3File] => Map[String, S3File]): Unit =
    buckets.updateAndGet(_ + (bucketName -> f(getBucket(bucketName))))

  override def download(bucketName: String, key: S3KeyName): Option[StreamWithMetadata] =
    getBucket(bucketName).get(key.value).map(_.toStreamWithMetadata)

  override def download(bucketName: String, key: S3KeyName, versionId: String): Option[StreamWithMetadata] =
    ??? // Ony used by S3TestController

  override def retrieveFileFromQuarantine(key: S3KeyName, versionId: String)(using ExecutionContext): Future[Option[FileData]] = {
    val bucketName = awsConfig.quarantineBucketName
    Future.successful(getBucket(bucketName).get(key.value).map(_.toFileData))
  }

  override def upload(bucketName: String, key: S3KeyName, file: ByteString, contentMd5: String): Future[PutObjectResponse] =
    updateBucket(bucketName)(_ + (key.value -> S3File(file.decodeString("UTF-8"), file.size)))
    Future.successful(PutObjectResponse.builder.build())

  override def listFilesInBucket(bucketName: String): Source[Seq[S3Object], NotUsed] =
    Source.single(
      getBucket(bucketName).toSeq.map {_ => S3Object.builder().build()}
    )

  override def copyFromQtoT(key: S3KeyName, versionId: String): Try[CopyObjectResponse] =
    scala.util.Try {
      val fromBucket = awsConfig.quarantineBucketName
      val toBucket   = awsConfig.transientBucketName
      val s3File = getBucket(fromBucket).getOrElse(key.value, sys.error(s"File $key was not found in bucket $fromBucket"))
      updateBucket(toBucket)(_ + (key.value -> s3File))
      CopyObjectResponse.builder.build()
    }

  override def getFileLengthFromQuarantine(key: S3KeyName, versionId: String): Long = {
    val bucketName = awsConfig.quarantineBucketName
    getBucket(bucketName).get(key.value).map(_.fileSize)
      .fold(sys.error(s"File $key was not found in bucket $bucketName"))(_.toLong)
  }

  override def getBucketProperties(bucketName: String): JsValue =
    ???  // Ony used by S3TestController

  override def deleteObjectFromBucket(bucketName: String, key: S3KeyName): Unit =
    updateBucket(bucketName)(_ - key.value)

  override def zipAndPresign(envelopeId: EnvelopeId, files: List[(FileId, Option[String])])(using ExecutionContext, Materializer): Future[ZipData] =
    ???
}
