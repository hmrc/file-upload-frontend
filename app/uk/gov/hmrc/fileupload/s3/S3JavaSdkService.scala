/*
 * Copyright 2023 HM Revenue & Customs
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

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.{ClosedShape, IOOperationIncompleteException, Materializer}
import org.apache.pekko.stream.connectors.file.ArchiveMetadata
import org.apache.pekko.stream.connectors.file.scaladsl.Archive
import org.apache.pekko.stream.scaladsl.{Broadcast, FileIO, Flow, GraphDSL, RunnableGraph, Sink, Source, StreamConverters}
import org.apache.pekko.util.ByteString
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
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}
import uk.gov.hmrc.fileupload.quarantine.FileData

import java.io.InputStream
import java.net.URL
import java.util.concurrent.Executors
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

@Singleton
class S3JavaSdkService @Inject()(
  configuration: com.typesafe.config.Config,
  metrics      : MetricRegistry
) extends S3Service:
  import S3JavaSdkService._

  private val logger = Logger(getClass)

  override val awsConfig = AwsConfig(configuration)

  val credentials = BasicAWSCredentials(awsConfig.accessKeyId, awsConfig.secretAccessKey)

  val metricGetObjectContent      = metrics.meter("s3.getObjectContent")
  val metricGetObjectContentSize  = metrics.counter("s3.getObjectContentSize")
  val metricGetObjectByKeyVersion = metrics.meter("s3.getObjectByKeyVersion")
  val metricGetObjectByKey        = metrics.meter("s3.getObjectByKey")
  val metricGetFileFromQuarantine = metrics.meter("s3.retrieveFileFromQuarantine")
  val metricUploadCompleted       = metrics.timer("s3.upload.completed")
  val metricUploadCompletedSize   = metrics.counter("s3.upload.completed.size")
  val metricUploadFailed          = metrics.meter("s3.upload.failed")
  val metricCopyFromQtoT          = metrics.meter("s3.copyFromQtoT")

  val proxyConfig =
    ClientConfiguration()
      .withProxyHost(awsConfig.proxyHost)
      .withProxyPort(awsConfig.proxyPort)
      .withProxyUsername(awsConfig.proxyUsername)
      .withProxyPassword(awsConfig.proxyPassword)
      .withConnectionTimeout(awsConfig.connectionTimeout)
      .withRequestTimeout(awsConfig.requestTimeout)
      .withSocketTimeout(awsConfig.socketTimeout)

  val s3Builder =
    AmazonS3ClientBuilder
      .standard()
      .withCredentials(AWSStaticCredentialsProvider(credentials))

  if (awsConfig.proxyEnabled)
    s3Builder.withClientConfiguration(proxyConfig)

  val s3Client =
    awsConfig.endpoint
      .fold(s3Builder.withRegion(Regions.EU_WEST_2)): endpoint =>
        s3Builder
          .withPathStyleAccessEnabled(true) // for localstack
          .withEndpointConfiguration(EndpointConfiguration(endpoint, "local-test"))
      .build()

  val transferManager =
    TransferManagerBuilder.standard()
      .withExecutorFactory:
        new ExecutorFactory:
          override def newExecutor() = Executors.newFixedThreadPool(25)
      .withS3Client(s3Client)
      .build()

  override def getFileLengthFromQuarantine(key: S3KeyName, versionId: String): Long =
    getFileLength(awsConfig.quarantineBucketName, key, versionId)

  def getFileLength(bucketName: String, key: S3KeyName, versionId: String): Long =
    getObjectMetadata(bucketName, key, versionId).getContentLength

  def getObjectMetadata(bucketName: String, key: S3KeyName, versionId: String): ObjectMetadata =
    s3Client.getObjectMetadata(GetObjectMetadataRequest(bucketName, key.value, versionId))

  def objectMetadataWithServerSideEncryption: ObjectMetadata =
    val om = ObjectMetadata()
    om.setSSEAlgorithm(SSEAlgorithm.KMS.getAlgorithm)
    om

  private def downloadByObject(s3Object: S3Object): StreamWithMetadata =
    val meta = s3Object.getObjectMetadata
    logger.info(s"Downloading $s3Object from S3")
    metricGetObjectContent.mark()
    metricGetObjectContentSize.inc(meta.getContentLength)
    StreamWithMetadata(
      StreamConverters
        .fromInputStream(() => s3Object.getObjectContent),
      Metadata(
        s3Object.getObjectMetadata.getContentType,
        s3Object.getObjectMetadata.getContentLength
      )
    )

  def objectByKeyVersion(bucketName: String, key: S3KeyName, versionId: String): Option[S3Object] =
    if s3Client.doesObjectExist(bucketName, key.value) then
      logger.info(s"Retrieving an existing S3 object from bucket: $bucketName with key: $key and version: $versionId")
      metricGetObjectByKeyVersion.mark()
      Some(s3Client.getObject(GetObjectRequest(bucketName, key.value, versionId)))
    else
      None

  def objectByKey(bucketName: String, key: S3KeyName): Option[S3Object] =
    if s3Client.doesObjectExist(bucketName, key.value) then
      logger.info(s"Retrieving an existing S3 object from bucket: $bucketName with key: $key)")
      metricGetObjectByKey.mark()
      Some(s3Client.getObject(GetObjectRequest(bucketName, key.value)))
    else
      None

  override def download(bucketName: String, key: S3KeyName, versionId: String): Option[StreamWithMetadata] =
    objectByKeyVersion(bucketName, key, versionId).map(downloadByObject)

  override def download(bucketName: String, key: S3KeyName): Option[StreamWithMetadata] =
    objectByKey(bucketName, key).map(downloadByObject)

  override def retrieveFileFromQuarantine(key: S3KeyName, versionId: String)(using ExecutionContext): Future[Option[FileData]] =
    Future:
      val s3Object     = s3Client.getObject(GetObjectRequest(awsConfig.quarantineBucketName, key.value, versionId))
      val objectDataIS = s3Object.getObjectContent
      val metadata     = s3Object.getObjectMetadata

      metricGetFileFromQuarantine.mark()

      Some:
        FileData(
          length      = metadata.getContentLength,
          filename    = s3Object.getKey,
          contentType = Some(metadata.getContentType),
          data        = objectDataIS
        )

  override def upload(bucketName: String, key: S3KeyName, file: InputStream, fileSize: Int): Future[UploadResult] =
    val metadata =
      val om = objectMetadataWithServerSideEncryption
      om.setContentLength(fileSize)
      om
    uploadFile(bucketName, key, file, metadata)

  def uploadFile(bucketName: String, key: S3KeyName, file: InputStream, metadata: ObjectMetadata): Future[UploadResult] =
    val fileInfo = s"bucket=$bucketName key=${key.value} fileSize=${metadata.getContentLength}"
    val uploadTime = metricUploadCompleted.time()
    Try(transferManager.upload(bucketName, key.value, file, metadata)) match
      case Success(upload) =>
        val promise = Promise[UploadResult]()
        logger.info(s"upload-s3 started: $fileInfo")
        upload.addProgressListener:
          new ProgressListener:
            var events:List[ProgressEvent] = List.empty
            def progressChanged(progressEvent: ProgressEvent): Unit =
              Try {
                events = progressEvent :: events
                if (progressEvent.getEventType == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
                  uploadTime.stop()
                  metricUploadCompletedSize.inc(metadata.getContentLength)
                  val resultTry = Try(upload.waitForUploadResult())
                  logger.info(s"upload-s3 completed: $fileInfo with success=${resultTry.isSuccess}")
                  promise.tryComplete(resultTry)
                } else if (progressEvent.getEventType == ProgressEventType.TRANSFER_FAILED_EVENT ||
                  upload.getState == TransferState.Failed || upload.getState == TransferState.Canceled
                ) {
                  metricUploadFailed.mark()
                  val exception = upload.waitForException()
                  logger.error(s"upload-s3 Transfer events: ${events.reverse.map(_.toString).mkString("\n")}")
                  logger.error(s"upload-s3 error: transfer failed: $fileInfo", exception)
                  promise.failure(Exception("transfer failed", exception))
                }
              }
        promise.future
      case Failure(ex) =>
        Future.failed(ex)

  override def listFilesInBucket(bucketName: String): Source[Seq[S3ObjectSummary], NotUsed] =
    Source.fromIterator(() => S3FilesIterator(s3Client, bucketName))

  override def copyFromQtoT(key: S3KeyName, versionId: String): Try[CopyObjectResult] =
    Try {
      logger.info(s"Copying a file key ${key.value} and version: $versionId")
      metricCopyFromQtoT.mark()
      val copyRequest = CopyObjectRequest(awsConfig.quarantineBucketName, key.value, versionId, awsConfig.transientBucketName, key.value)
      copyRequest.setNewObjectMetadata(objectMetadataWithServerSideEncryption)
      s3Client.copyObject(copyRequest)
    }

  override def getBucketProperties(bucketName: String): JsObject =
    val versioningStatus = s3Client.getBucketVersioningConfiguration(bucketName).getStatus
    Json.obj("versioningStatus" -> versioningStatus)

  override def deleteObjectFromBucket(bucketName: String, key: S3KeyName): Unit =
    val summaries = s3Client.listVersions(bucketName, key.value).getVersionSummaries

    val objectDescription = ReflectionToStringBuilder.toString(s3Client.getObjectMetadata(bucketName, key.value))

    logger.info(s"Deleting object. Object ${key.value} from bucket $bucketName has ${summaries.size()} versions. Description: $objectDescription")

    for (summary: S3VersionSummary <- summaries.asScala) {
      val outcome = Try(s3Client.deleteVersion(bucketName, summary.getKey, summary.getVersionId))
      logger.info(s"Outcome of deleting ${key.value} / ${summary.getVersionId}: $outcome")
    }

  override def zipAndPresign(
    envelopeId: EnvelopeId,
    files     : List[(FileId, Option[String])]
  )(using
    ExecutionContext,
    Materializer
  ): Future[ZipData] =
    val fileName = s"$envelopeId.zip"
    val tempFile = SingletonTemporaryFileCreator.create(prefix = "zip")
    val (uploadFinished, md5Finished) =
      broadcast2(
        source = zipSource(envelopeId, files),
        sink1  = FileIO.toPath(tempFile.path),
        sink2  = Md5Hash.md5HashSink
      ).run()
    (for
       _            <- Future.successful(logger.debug(s"zipping $envelopeId to ${tempFile.path}"))
       _            <- uploadFinished
       md5Hash      <- md5Finished
       fileSize     =  tempFile.path.toFile.length
       _            =  logger.debug(s"uploading $envelopeId to S3")
       uploadResult <- uploadFile(
                         bucketName = awsConfig.transientBucketName,
                         key        = S3Key.forZipSubdir(awsConfig.zipSubdir)(fileName),
                         file       = java.io.FileInputStream(tempFile.path.toFile),
                         metadata   = { val om = ObjectMetadata()
                                        om.setSSEAlgorithm(SSEAlgorithm.KMS.getAlgorithm)
                                        om.setContentLength(fileSize)
                                        om.setContentType("application/zip")
                                        om.setContentMD5(md5Hash)
                                        om
                                      }
                       )
       _            =  logger.debug(s"presigning $envelopeId")
       url          =  presign(
                         bucketName         = uploadResult.getBucketName,
                         key                = uploadResult.getKey,
                         expirationDuration = awsConfig.zipDuration
                       )
     yield
       ZipData(
         name        = fileName,
         size        = fileSize,
         md5Checksum = md5Hash,
         url         = url
       )
    ).recover { case e: IOOperationIncompleteException if e.getCause.isInstanceOf[MissingFileException] => throw e.getCause }
     .andThen { case _ => SingletonTemporaryFileCreator.delete(tempFile) }

  end zipAndPresign

  private def broadcast2[T, Mat1, Mat2](
    source: Source[T, Any],
    sink1: Sink[T, Mat1],
    sink2: Sink[T, Mat2]
  ): RunnableGraph[(Mat1, Mat2)] =
    RunnableGraph.fromGraph:
      GraphDSL.createGraph(sink1, sink2)(Tuple2.apply):
        implicit builder => (s1, s2) =>
          import GraphDSL.Implicits._
          val broadcast = builder.add(Broadcast[T](2))
          source ~> broadcast
          broadcast.out(0) ~> Flow[T].async ~> s1
          broadcast.out(1) ~> Flow[T].async ~> s2
          ClosedShape

  def zipSource(
    envelopeId: EnvelopeId,
    files     : List[(FileId, Option[String])]
  )(using ExecutionContext
  ): Source[(ArchiveMetadata, S3Service.StreamResult), NotUsed]#Repr[ByteString] =
    // dedupe filenames since file-upload doesn't prevent multiple files with the same name
    val filesUnique = dedupeFilenames(envelopeId, files)

    // load as an iterator to avoid downloading multiple files at once - aws connection pool is limited
    // also since streams must be consumed for the connection to be returned, we don't want to get any prematurely
    Source
      .fromIterator: () =>
        filesUnique.iterator.map: (fileId, filename) =>
          download(
            bucketName = awsConfig.transientBucketName,
            key        = S3Key.forEnvSubdir(awsConfig.envSubdir)(envelopeId, fileId)
          ) match
            case None => throw MissingFileException(s"Could not find file $fileId, for envelope $envelopeId")
            case Some(streamWithMetadata) => ( ArchiveMetadata(filename)
                                             , streamWithMetadata.stream
                                                .mapMaterializedValue(
                                                  _.map { res => logger.debug(s"Finished reading stream for fileId $fileId in envelope $envelopeId to zip: $res"); res }
                                                )
                                             )
      .via(Archive.zip())
      .mapError:
        case e: MissingFileException => e
        case t: Throwable => RuntimeException(s"Failed to create zip for envelope $envelopeId: ${t.getMessage}", t)

  end zipSource

  def presign(bucketName: String, key: String, expirationDuration: Duration): URL =
    import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest

    val expiration = java.util.Date()
    expiration.setTime(expiration.getTime + expirationDuration.toMillis)

    s3Client.generatePresignedUrl:
      GeneratePresignedUrlRequest(bucketName, key)
        .withMethod(HttpMethod.GET)
        .withExpiration(expiration)

end S3JavaSdkService

object S3JavaSdkService:
  private val logger = Logger(getClass)

  // ensure no duplicate file names
  def dedupeFilenames(envelopeId: EnvelopeId, files: List[(FileId, Option[String])]): List[(FileId, String)] =
    files
      .groupBy(_._2)
      .view.mapValues(_.map(_._1))
      .toList
      .flatMap:
        case (None          , fileIds    ) => fileIds.map(_ -> UUID.randomUUID().toString)
        case (Some(fileName), Seq(fileId)) => Seq(fileId -> fileName)
        case (Some(fileName), fileIds    ) => logger.warn(s"Duplicate filename have been provided for envelope $envelopeId, they will be renamed")
                                              lazy val (name, ext) =
                                                val pos = fileName.lastIndexOf('.')
                                                if (pos == -1) (fileName, "")
                                                else fileName.splitAt(pos)

                                              fileIds.zipWithIndex.map:
                                                case (fileId, 0) => (fileId, fileName)
                                                case (fileId, i) => (fileId, name + "-" + i + ext)

end S3JavaSdkService

class S3FilesIterator(s3Client: AmazonS3, bucketName: String) extends Iterator[Seq[S3ObjectSummary]]:

  Logger(getClass).info(s"Listing objects from bucket $bucketName")

  private var listing = s3Client.listObjects(bucketName)
  private val summaries = listing.getObjectSummaries
  private var _hasNext = !summaries.isEmpty

  def hasNext = _hasNext

  def next() =
    val current = summaries
    listing = s3Client.listNextBatchOfObjects(listing)
    _hasNext = listing.isTruncated
    current.asScala.toSeq

end S3FilesIterator
