/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.s3

import java.io.{File, InputStream}
import java.net.URL
import java.util.concurrent.Executors
import java.util.{Base64, UUID}
import java.security.{DigestInputStream, MessageDigest}

import akka.stream.Materializer
import akka.stream.alpakka.file.ArchiveMetadata
import akka.stream.alpakka.file.scaladsl.Archive
import akka.stream.scaladsl.{FileIO, Source, StreamConverters}
import com.amazonaws.{ClientConfiguration, HttpMethod}
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.client.builder.ExecutorFactory
import com.amazonaws.event.{ProgressEvent, ProgressEventType, ProgressListener}
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model._
import com.amazonaws.services.s3.transfer.Transfer.TransferState
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.amazonaws.services.s3.transfer.model.UploadResult
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.codahale.metrics.MetricRegistry
import org.apache.commons.lang3.builder.ReflectionToStringBuilder
import play.api.Logger
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}
import uk.gov.hmrc.fileupload.quarantine.FileData

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

class S3JavaSdkService(configuration: com.typesafe.config.Config, metrics: MetricRegistry) extends S3Service {
  override val awsConfig = new AwsConfig(configuration)

  val credentials = new BasicAWSCredentials(awsConfig.accessKeyId, awsConfig.secretAccessKey)

  val metricGetObjectContent = metrics.meter("s3.getObjectContent")
  val metricGetObjectContentSize = metrics.counter("s3.getObjectContentSize")
  val metricGetObjectByKeyVersion = metrics.meter("s3.getObjectByKeyVersion")
  val metricGetObjectByKey = metrics.meter("s3.getObjectByKey")
  val metricGetFileFromQuarantine = metrics.meter("s3.retrieveFileFromQuarantine")
  val metricUploadCompleted = metrics.timer("s3.upload.completed")
  val metricUploadCompletedSize = metrics.counter("s3.upload.completed.size")
  val metricUploadFailed = metrics.meter("s3.upload.failed")
  val metricCopyFromQtoT = metrics.meter("s3.copyFromQtoT")

  awsConfig.proxyEnabled
  val proxyConfig = new ClientConfiguration()
    .withProxyHost(awsConfig.proxyHost)
    .withProxyPort(awsConfig.proxyPort)
    .withProxyUsername(awsConfig.proxyUsername)
    .withProxyPassword(awsConfig.proxyPassword)
    .withConnectionTimeout(awsConfig.connectionTimeout)
    .withRequestTimeout(awsConfig.requestTimeout)
    .withSocketTimeout(awsConfig.socketTimeout)

  val s3Builder = AmazonS3ClientBuilder
    .standard()
    .withCredentials(new AWSStaticCredentialsProvider(credentials))

  if (awsConfig.proxyEnabled)
    s3Builder.withClientConfiguration(proxyConfig)

  val s3Client = awsConfig.endpoint.fold(
    s3Builder.withRegion(Regions.EU_WEST_2)
  ) { endpoint =>
    s3Builder
      .withPathStyleAccessEnabled(true) // for localstack
      .withEndpointConfiguration(new EndpointConfiguration(endpoint, "local-test"))
  }.build()

  val transferManager =
    TransferManagerBuilder.standard()
      .withExecutorFactory(new ExecutorFactory {
        def newExecutor() = Executors.newFixedThreadPool(25)
      })
      .withS3Client(s3Client)
      .build()

  override def getFileLengthFromQuarantine(key: String, versionId: String): Long =
    getFileLength(awsConfig.quarantineBucketName, key, versionId)

  def getFileLength(bucketName: String, key: String, versionId: String): Long =
    getObjectMetadata(bucketName, key, versionId).getContentLength

  def getObjectMetadata(bucketName: String, key: String, versionId: String): ObjectMetadata =
    s3Client.getObjectMetadata(new GetObjectMetadataRequest(bucketName, key, versionId))

  def objectMetadataWithServerSideEncryption: ObjectMetadata = {
    val om = new ObjectMetadata()
    om.setSSEAlgorithm(SSEAlgorithm.KMS.getAlgorithm)
    om
  }

  private def downloadByObject(s3Object: S3Object): StreamWithMetadata = {
    val meta = s3Object.getObjectMetadata
    Logger.info(s"Downloading $s3Object from S3")
    metricGetObjectContent.mark()
    metricGetObjectContentSize.inc(meta.getContentLength)
    StreamWithMetadata(
      StreamConverters
        .fromInputStream(() => s3Object.getObjectContent),
      Metadata(
        s3Object.getObjectMetadata.getContentType,
        s3Object.getObjectMetadata.getContentLength
      ))
  }

  def objectByKeyVersion(bucketName: String, key: S3KeyName, versionId: String): Option[S3Object] =
    if (s3Client.doesObjectExist(bucketName, key.value)) {
      Logger.info(s"Retrieving an existing S3 object from bucket: $bucketName with key: $key and version: $versionId")
      metricGetObjectByKeyVersion.mark()
      Some(s3Client.getObject(new GetObjectRequest(bucketName, key.value, versionId)))
    }
    else None

  def objectByKey(bucketName: String, key: S3KeyName): Option[S3Object] =
    if (s3Client.doesObjectExist(bucketName, key.value)) {
      Logger.info(s"Retrieving an existing S3 object from bucket: $bucketName with key: $key)")
      metricGetObjectByKey.mark()
      Some(s3Client.getObject(new GetObjectRequest(bucketName, key.value)))
    }
    else None

  override def download(bucketName: String, key: S3KeyName, versionId: String): Option[StreamWithMetadata] =
    objectByKeyVersion(bucketName, key, versionId).map(downloadByObject)

  override def download(bucketName: String, key: S3KeyName): Option[StreamWithMetadata] =
    objectByKey(bucketName, key).map(downloadByObject)

  override def retrieveFileFromQuarantine(key: String, versionId: String)(implicit ec: ExecutionContext): Future[Option[FileData]] =
    Future {
      val s3Object = s3Client.getObject(new GetObjectRequest(awsConfig.quarantineBucketName, key, versionId))
      val objectDataIS = s3Object.getObjectContent
      val metadata = s3Object.getObjectMetadata

      metricGetFileFromQuarantine.mark()

      Some(FileData(
        length      = metadata.getContentLength,
        filename    = s3Object.getKey,
        contentType = Some(metadata.getContentType),
        data        = objectDataIS
      ))
    }

  override def upload(bucketName: String, key: String, file: InputStream, fileSize: Int): Future[UploadResult] =
    uploadFile(bucketName, key, file,
      metadata = {
        val om = objectMetadataWithServerSideEncryption
        om.setContentLength(fileSize)
        om
      }
    )

  def uploadFile(bucketName: String, key: String, file: InputStream, metadata: ObjectMetadata): Future[UploadResult] = {
    val fileInfo = s"bucket=$bucketName key=$key fileSize=${metadata.getContentLength}"
    val uploadTime = metricUploadCompleted.time()
    Try(transferManager.upload(bucketName, key, file, metadata)) match {
      case Success(upload) =>
        val promise = Promise[UploadResult]
        Logger.info(s"upload-s3 started: $fileInfo")
        upload.addProgressListener(new ProgressListener {
          var events:List[ProgressEvent] = List.empty
          def progressChanged(progressEvent: ProgressEvent): Unit = Try {
            events = progressEvent :: events
            if (progressEvent.getEventType == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
              uploadTime.stop()
              metricUploadCompletedSize.inc(metadata.getContentLength)
              val resultTry = Try(upload.waitForUploadResult())
              Logger.info(s"upload-s3 completed: $fileInfo with success=${resultTry.isSuccess}")
              promise.tryComplete(resultTry)
            } else if (progressEvent.getEventType == ProgressEventType.TRANSFER_FAILED_EVENT ||
              upload.getState == TransferState.Failed || upload.getState == TransferState.Canceled
            ) {
              metricUploadFailed.mark()
              val exception = upload.waitForException()
              Logger.error(s"upload-s3 Transfer events: ${events.reverse.map(_.toString).mkString("\n")}")
              Logger.error(s"upload-s3 error: transfer failed: $fileInfo", exception)
              promise.failure(new Exception("transfer failed", exception))
            }
          }
        })
        promise.future
      case Failure(ex) => Future.failed(ex)
    }
  }

  override def listFilesInBucket(bucketName: String): Source[Seq[S3ObjectSummary], akka.NotUsed] =
    Source.fromIterator(() => new S3FilesIterator(s3Client, bucketName))

  override def copyFromQtoT(key: String, versionId: String): Try[CopyObjectResult] = Try {
    Logger.info(s"Copying a file key $key and version: $versionId")
    metricCopyFromQtoT.mark()
    val copyRequest = new CopyObjectRequest(awsConfig.quarantineBucketName, key, versionId, awsConfig.transientBucketName, key)
    copyRequest.setNewObjectMetadata(objectMetadataWithServerSideEncryption)
    s3Client.copyObject(copyRequest)
  }

  override def getBucketProperties(bucketName: String): JsObject = {
    val versioningStatus = s3Client.getBucketVersioningConfiguration(bucketName).getStatus
    Json.obj(
      "versioningStatus" -> versioningStatus
    )
  }

  override def deleteObjectFromBucket(bucketName: String, key: String): Unit = {

    val summaries = s3Client.listVersions(bucketName, key).getVersionSummaries

    val objectDescription = ReflectionToStringBuilder.toString(s3Client.getObjectMetadata(bucketName, key))

    Logger.info(s"Deleting object. Object $key from bucket $bucketName has ${summaries.size()} versions. Description: $objectDescription")

    for (summary: S3VersionSummary <- summaries.asScala) {
      val outcome = Try(s3Client.deleteVersion(bucketName, summary.getKey, summary.getVersionId))
      Logger.info(s"Outcome of deleting $key / ${summary.getVersionId}: $outcome")
    }
  }

  override def zipAndPresign(
    envelopeId: EnvelopeId,
    files     : List[(FileId, Option[String])]
  )(implicit
    ec          : ExecutionContext,
    materializer: Materializer
  ): Future[ZipData] = {
    val fileName = s"$envelopeId.zip"
    val tempFile = TemporaryFile(prefix = "zip")
    (for {
       _            <- zipToFile(envelopeId, files, tempFile.file)
       fileSize     =  tempFile.file.length
       is           =  new java.io.FileInputStream(tempFile.file)

       // decorate inputstream so we can calculate checksum on same pass
       md           =  MessageDigest.getInstance("MD5")
       dis          =  new DigestInputStream(is, md)

       uploadResult <- uploadFile(
                         bucketName = awsConfig.transientBucketName,
                         key        = S3Key.forZipSubdir(awsConfig.zipSubdir)(fileName),
                         file       = dis,
                         metadata   = { val om = new ObjectMetadata()
                                        om.setSSEAlgorithm(SSEAlgorithm.KMS.getAlgorithm)
                                        om.setContentLength(fileSize)
                                        om.setContentType("application/zip")
                                        om
                                      }
                       )
       url          =  presign(
                         bucketName         = uploadResult.getBucketName,
                         key                = uploadResult.getKey,
                         expirationDuration = awsConfig.zipDuration
                       )
     } yield
         ZipData(
           name        = fileName,
           size        = fileSize,
           md5Checksum = Base64.getEncoder().encodeToString(md.digest()),
           url         = url
         )
    ).andThen { case _ => tempFile.clean() }
  }

  def zipToFile(
    envelopeId: EnvelopeId,
    files     : List[(FileId, Option[String])],
    targetFile: File
  )(implicit materializer: Materializer): Future[akka.stream.IOResult] =
    Source(
      files.map { case (fileId, name) =>
        val filename = name.getOrElse(UUID.randomUUID().toString)
        download(
          bucketName = awsConfig.transientBucketName,
          key        = S3KeyName(S3Key.forEnvSubdir(awsConfig.envSubdir)(envelopeId, fileId))
        ) match {
          case None => sys.error(s"Could not find file $fileId, for envelope $envelopeId")
          case Some(streamWithMetadata) => (ArchiveMetadata(filename), streamWithMetadata.stream)
        }
      }
    ).via(Archive.zip())
     .runWith(FileIO.toPath(targetFile.toPath))

  def presign(bucketName: String, key: String, expirationDuration: Duration): URL = {
    import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest

    val expiration = new java.util.Date
    expiration.setTime(expiration.getTime + expirationDuration.toMillis)

    s3Client.generatePresignedUrl(
      new GeneratePresignedUrlRequest(bucketName, key)
        .withMethod(HttpMethod.GET)
        .withExpiration(expiration)
    )
  }
}

class S3FilesIterator(s3Client: AmazonS3, bucketName: String) extends Iterator[Seq[S3ObjectSummary]] {

  Logger.info(s"Listing objects from bucket $bucketName")

  var listing = s3Client.listObjects(bucketName)
  val summaries = listing.getObjectSummaries
  var _hasNext = !summaries.isEmpty

  def hasNext = _hasNext

  def next() = {
    val current = summaries
    listing = s3Client.listNextBatchOfObjects(listing)
    _hasNext = listing.isTruncated
    current.asScala
  }
}
