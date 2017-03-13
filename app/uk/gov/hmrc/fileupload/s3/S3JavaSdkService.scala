/*
 * Copyright 2017 HM Revenue & Customs
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

import akka.NotUsed
import akka.stream.IOResult
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.ExecutorFactory
import com.amazonaws.event.{ProgressEvent, ProgressEventType, ProgressListener}
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.model.{GetObjectRequest, ObjectMetadata, S3ObjectSummary, SSEAlgorithm}
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.amazonaws.services.s3.transfer.model.UploadResult
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import play.api.libs.iteratee.Enumerator
import uk.gov.hmrc.fileupload.quarantine.FileData
import uk.gov.hmrc.fileupload.s3.S3Service._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, Promise}

trait S3Service {
  def awsConfig: AwsConfig

  def download(bucketName: String, key:  String): Source[ByteString, Future[IOResult]]

  def download(bucketName: String, key: String, versionId: String): Source[ByteString, Future[IOResult]]

  def retrieveFileFromQuarantine(key : String, versionId: String)(implicit ec: ExecutionContext) : Future[Option[FileData]]

  def upload(bucketName: String, key: String, file: InputStream, fileSize: Int): Future[UploadResult]

  def uploadToQuarantine: UploadToQuarantine = upload(awsConfig.quarantineBucketName, _, _, _)

  def listFilesInBucket(bucketName: String): Source[Seq[S3ObjectSummary], NotUsed]

  def listFilesInQuarantine: Source[Seq[S3ObjectSummary], NotUsed] =
    listFilesInBucket(awsConfig.quarantineBucketName)

  def listFilesInTransient: Source[Seq[S3ObjectSummary], NotUsed] =
    listFilesInBucket(awsConfig.transientBucketName)
}

object S3Service {
  type UploadToQuarantine = (String, InputStream, Int) => Future[UploadResult]
}

class S3JavaSdkService extends S3Service {
  val awsConfig = new AwsConfig()

  val credentials = new BasicAWSCredentials(awsConfig.accessKeyId, awsConfig.secretAccessKey)

  val s3Client =
    AmazonS3ClientBuilder
      .standard()
      .withCredentials(new AWSStaticCredentialsProvider(credentials))
      .withRegion(Regions.EU_WEST_2)
      .build()

//  // localhost client
//  val s3Client = {
//    val credentials = new BasicAWSCredentials(awsConfig.accessKeyId, awsConfig.secretAccessKey)
//    val s3 = new AmazonS3Client(credentials)
//    s3.setEndpoint("http://localhost:8001")
//    s3.setS3ClientOptions(new S3ClientOptions().withPathStyleAccess(true))
//    s3
//  }

  val transferManager =
    TransferManagerBuilder.standard()
      .withExecutorFactory(new ExecutorFactory { def newExecutor() = Executors.newFixedThreadPool(25) })
      .withS3Client(s3Client)
      .build()

  def objectMetadata(fileSize: Int) = {
    val om = new ObjectMetadata()
    om.setContentLength(fileSize)
    om.setSSEAlgorithm(SSEAlgorithm.KMS.getAlgorithm)
    om
  }

  def download(bucketName: String, key:  String) =
    StreamConverters
      .fromInputStream(() => s3Client.getObject(bucketName, key).getObjectContent)

  def download(bucketName: String, key: String, versionId: String) =
    StreamConverters
      .fromInputStream { () =>
        s3Client.getObject(new GetObjectRequest(bucketName, key, versionId)).getObjectContent
      }


  override def retrieveFileFromQuarantine(key: String, versionId: String)(implicit ec: ExecutionContext) = {
    Future {
      val s3Object = s3Client.getObject(new GetObjectRequest(awsConfig.quarantineBucketName, key, versionId))
      val objectDataIS = s3Object.getObjectContent
      val metadata = s3Object.getObjectMetadata

      Some(FileData(length = metadata.getContentLength, filename = s3Object.getKey,
        contentType = Some(metadata.getContentType), data = Enumerator.fromStream(objectDataIS)))
    }
  }

  def upload(bucketName: String, key: String, file: InputStream, fileSize: Int): Future[UploadResult] = {
    val upload = transferManager.upload(bucketName, key, file, objectMetadata(fileSize))
    val promise = Promise[UploadResult]
    upload.addProgressListener(new ProgressListener {
      def progressChanged(progressEvent: ProgressEvent) = {
        if (progressEvent.getEventType == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
          promise.trySuccess(upload.waitForUploadResult())
        } else if (progressEvent.getEventType == ProgressEventType.TRANSFER_FAILED_EVENT) {
          promise.failure(new Exception("transfer failed"))
        }
        // handle other event types?
      }
    })
    promise.future
  }

  def listFilesInBucket(bucketName: String) = {
    Source.fromIterator(() => new S3FilesIterator(s3Client, bucketName))
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
