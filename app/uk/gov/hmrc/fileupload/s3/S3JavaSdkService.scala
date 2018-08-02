/*
 * Copyright 2018 HM Revenue & Customs
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

import java.io.InputStream
import java.util.concurrent.Executors

import akka.stream.scaladsl.{Source, StreamConverters}
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.client.builder.ExecutorFactory
import com.amazonaws.event.{ProgressEvent, ProgressEventType, ProgressListener}
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model._
import com.amazonaws.services.s3.transfer.Transfer.TransferState
import com.amazonaws.services.s3.transfer.{TransferManagerBuilder, Upload}
import com.amazonaws.services.s3.transfer.model.UploadResult
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.codahale.metrics.MetricRegistry
import play.api.Logger
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.quarantine.FileData

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

class S3JavaSdkService(configuration: com.typesafe.config.Config, metrics: MetricRegistry) extends S3Service {
  val awsConfig = new AwsConfig(configuration)

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
    s3Builder.withEndpointConfiguration(new EndpointConfiguration(endpoint, "local-test"))
  }.build()

  val transferManager =
    TransferManagerBuilder.standard()
      .withExecutorFactory(new ExecutorFactory {
        def newExecutor() = Executors.newFixedThreadPool(25)
      })
      .withS3Client(s3Client)
      .build()

  def objectMetadata(fileSize: Int) = {
    val om = objectMetadataWithServerSideEncryption
    om.setContentLength(fileSize)
    om
  }

  def getFileLengthFromQuarantine(key: String, versionId: String) =
    getFileLength(awsConfig.quarantineBucketName, key, versionId)

  def getFileLength(bucketName: String, key: String, versionId: String): Long = {
    getObjectMetadata(bucketName, key, versionId).getContentLength
  }

  def getObjectMetadata(bucketName: String, key: String, versionId: String): ObjectMetadata = {
    s3Client.getObjectMetadata(new GetObjectMetadataRequest(bucketName, key, versionId))
  }

  def objectMetadataWithServerSideEncryption: ObjectMetadata = {
    val om = new ObjectMetadata()
    om.setSSEAlgorithm(SSEAlgorithm.KMS.getAlgorithm)
    om
  }

  private def downloadByObject(s3Object: S3Object) = {
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

  def objectByKeyVersion(bucketName: String, key: S3KeyName, versionId: String): Option[S3Object] = {
    if (s3Client.doesObjectExist(bucketName, key.value)) {
      Logger.info(s"Retrieving an existing S3 object from bucket: $bucketName with key: $S3KeyName and version: $versionId")
      metricGetObjectByKeyVersion.mark()
      Some(s3Client.getObject(new GetObjectRequest(bucketName, key.value, versionId)))
    }
    else None
  }

  def objectByKey(bucketName: String, key: S3KeyName): Option[S3Object] = {
    if (s3Client.doesObjectExist(bucketName, key.value)) {
      Logger.info(s"Retrieving an existing S3 object from bucket: $bucketName with key: $S3KeyName)")
      metricGetObjectByKey.mark()
      Some(s3Client.getObject(new GetObjectRequest(bucketName, key.value)))
    }
    else None
  }

  def download(bucketName: String, key: S3KeyName, versionId: String) =
    objectByKeyVersion(bucketName, key, versionId).map(downloadByObject)

  def download(bucketName: String, key: S3KeyName) =
    objectByKey(bucketName, key).map(downloadByObject)

  override def retrieveFileFromQuarantine(key: String, versionId: String)(implicit ec: ExecutionContext) = {
    Future {
      val s3Object = s3Client.getObject(new GetObjectRequest(awsConfig.quarantineBucketName, key, versionId))
      val objectDataIS = s3Object.getObjectContent
      val metadata = s3Object.getObjectMetadata

      metricGetFileFromQuarantine.mark()

      Some(FileData(length = metadata.getContentLength, filename = s3Object.getKey,
        contentType = Some(metadata.getContentType), data = Enumerator.fromStream(objectDataIS)))
    }
  }

  def upload(bucketName: String, key: String, file: InputStream, fileSize: Int): Future[UploadResult] = {
    val fileInfo = s"bucket=$bucketName key=$key fileSize=$fileSize"
    val uploadTime = metricUploadCompleted.time()
    Try( transferManager.upload(bucketName, key, file, objectMetadata(fileSize)) ) match {
      case Success(upload) =>
        val promise = Promise[UploadResult]
        Logger.info(s"upload-s3 started: $fileInfo")
        upload.addProgressListener(new ProgressListener {
          var events:List[ProgressEvent] = List.empty
          def progressChanged(progressEvent: ProgressEvent): Unit = Try{
            events = progressEvent :: events
            if (progressEvent.getEventType == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
              uploadTime.stop()
              metricUploadCompletedSize.inc(fileSize)
              val resultTry = Try({
                upload.waitForUploadResult()
              })
              Logger.info(s"upload-s3 completed: $fileInfo with success=${resultTry.isSuccess}")
              promise.tryComplete(resultTry)
            } else if (progressEvent.getEventType == ProgressEventType.TRANSFER_FAILED_EVENT ||
              upload.getState == TransferState.Failed || upload.getState == TransferState.Canceled
            ) {
              metricUploadFailed.mark()
              val exception = upload.waitForException()
              Logger.error(s"""upload-s3 Transfer events: ${events.reverse.map(_.toString).mkString("\n")}""")
              Logger.error(s"upload-s3 error: transfer failed: $fileInfo", exception)
              promise.failure(new Exception("transfer failed", exception))
            }
          }
        })
        promise.future
      case Failure(ex) => Future.failed(ex)
    }
  }

  def listFilesInBucket(bucketName: String) = {
    Source.fromIterator(() => new S3FilesIterator(s3Client, bucketName))
  }

  def copyFromQtoT(key: String, versionId: String): Try[CopyObjectResult] = Try {
    Logger.info(s"Copying a file key $key and version: $versionId")
    metricCopyFromQtoT.mark()
    val copyRequest = new CopyObjectRequest(awsConfig.quarantineBucketName, key, versionId, awsConfig.transientBucketName, key)
    copyRequest.setNewObjectMetadata(objectMetadataWithServerSideEncryption)
    s3Client.copyObject(copyRequest)
  }

  def getBucketProperties(bucketName: String) = {
    val versioningStatus = s3Client.getBucketVersioningConfiguration(bucketName).getStatus
    Json.obj(
      "versioningStatus" -> versioningStatus
    )
  }

  def deleteObjectFromBucket(bucketName: String, key: String): Unit = {
    Try(s3Client.deleteObject(bucketName, key)) match {
      case Success(_) =>
        Logger.info(s"Objected successfully deleted with key $key from bucket $bucketName")
        ()
      case Failure(error) =>
        Logger.error(s"Attempted to delete object with key $key from bucket $bucketName but error thrown: ${error.getMessage}", error)
        throw error
    }
  }
}

class S3FilesIterator(s3Client: AmazonS3, bucketName: String) extends Iterator[Seq[S3ObjectSummary]] {
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
