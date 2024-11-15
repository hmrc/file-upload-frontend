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

import com.codahale.metrics.MetricRegistry
import org.apache.commons.lang3.builder.ReflectionToStringBuilder
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.{ClosedShape, IOOperationIncompleteException, Materializer}
import org.apache.pekko.stream.connectors.file.ArchiveMetadata
import org.apache.pekko.stream.connectors.file.scaladsl.Archive
import org.apache.pekko.stream.scaladsl.{Broadcast, FileIO, Flow, GraphDSL, RunnableGraph, Sink, Source, StreamConverters}
import org.apache.pekko.util.ByteString
import play.api.Logger
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.libs.json.{JsObject, Json}
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model._
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}
import uk.gov.hmrc.fileupload.quarantine.FileData

import java.io.InputStream
import java.net.{URI, URL}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.Try

@Singleton
class S3JavaSdkService @Inject()(
  configuration: com.typesafe.config.Config,
  metrics      : MetricRegistry
)(using
  ExecutionContext
) extends S3Service:
  import S3JavaSdkService._

  private val logger = Logger(getClass)

  override val awsConfig = AwsConfig(configuration)

  private val credentials = AwsBasicCredentials.create(awsConfig.accessKeyId, awsConfig.secretAccessKey)

  val metricGetObjectContent      = metrics.meter("s3.getObjectContent")
  val metricGetObjectContentSize  = metrics.counter("s3.getObjectContentSize")
  val metricGetObjectByKeyVersion = metrics.meter("s3.getObjectByKeyVersion")
  val metricGetObjectByKey        = metrics.meter("s3.getObjectByKey")
  val metricGetFileFromQuarantine = metrics.meter("s3.retrieveFileFromQuarantine")
  val metricUploadCompleted       = metrics.timer("s3.upload.completed")
  val metricUploadCompletedSize   = metrics.counter("s3.upload.completed.size")
  val metricUploadFailed          = metrics.meter("s3.upload.failed")
  val metricCopyFromQtoT          = metrics.meter("s3.copyFromQtoT")

  val s3Client =
    val s3Builder =
      S3Client
        .builder()
        .credentialsProvider(StaticCredentialsProvider.create(credentials))

    awsConfig.endpoint
      .fold(s3Builder.region(Region.EU_WEST_2)): endpoint =>
        s3Builder
          .forcePathStyle(true) // for localstack
          .endpointOverride(URI.create(endpoint))
      .build()

  override def getFileLengthFromQuarantine(key: S3KeyName, versionId: String): Long =
    getFileLength(awsConfig.quarantineBucketName, key, versionId)

  private def getFileLength(bucketName: String, key: S3KeyName, versionId: String): Long =
    getObjectMetadata(bucketName, key, Some(versionId)).contentLength

  private def getObjectMetadata(bucketName: String, key: S3KeyName, versionId: Option[String]): HeadObjectResponse =
    val request =
      HeadObjectRequest
        .builder()
        .bucket(bucketName)
        .key(key.value)
    versionId.foreach(request.versionId)

    s3Client.headObject(request.build())

  // we could just download the object and handle the NoSuchKeyException then, but it would affect current metrics
  private def doesObjectExist(bucketName: String, key: S3KeyName, versionId: Option[String]): Boolean =
    try
      val request =
        HeadObjectRequest
          .builder()
          .bucket(bucketName)
          .key(key.value)
      versionId.foreach(request.versionId)
      s3Client.headObject:
        request.build()
      true
    catch
      case e: NoSuchKeyException =>
        false

  private def download(bucketName: String, key: S3KeyName, versionId: Option[String]): Option[StreamWithMetadata] =
    if doesObjectExist(bucketName, key, versionId) then
      logger.info(s"Retrieving an existing S3 object from bucket: $bucketName with key: $key ${versionId.fold("")(versionId => s"and version: $versionId")}")

      versionId match
        case Some(_) => metricGetObjectByKeyVersion.mark()
        case None    => metricGetObjectByKey.mark()

      val request =
        GetObjectRequest.builder().bucket(bucketName).key(key.value)
      versionId.foreach(request.versionId)

      val result = s3Client.getObject(request.build())
      metricGetObjectContent.mark()
      metricGetObjectContentSize.inc(result.response.contentLength)
      Some:
        StreamWithMetadata(
          StreamConverters.fromInputStream(() => result),
          Metadata(
            result.response.contentType,
            result.response.contentLength
          )
        )
    else
      None

  override def download(bucketName: String, key: S3KeyName, versionId: String): Option[StreamWithMetadata] =
    download(bucketName, key, Some(versionId))

  override def download(bucketName: String, key: S3KeyName): Option[StreamWithMetadata] =
    download(bucketName, key, None)

  override def retrieveFileFromQuarantine(key: S3KeyName, versionId: String)(using ExecutionContext): Future[Option[FileData]] =
    Future:
      val result =
        s3Client.getObject:
          GetObjectRequest
            .builder()
            .bucket(awsConfig.quarantineBucketName)
            .key(key.value)
            .versionId(versionId)
            .build()

      metricGetFileFromQuarantine.mark()

      Some:
        FileData(
          length      = result.response.contentLength,
          filename    = key.value,
          contentType = Some(result.response.contentType),
          data        = result
        )

  override def upload(bucketName: String, key: S3KeyName, file: InputStream, fileSize: Int): Future[PutObjectResponse] =
    uploadFile(bucketName, key, file, fileSize.toLong, None, None)

  def uploadFile(bucketName: String, key: S3KeyName, file: InputStream, fileSize: Long, contentType: Option[String], contentMd5: Option[String]): Future[PutObjectResponse] =
    val fileInfo = s"bucket=$bucketName key=${key.value} fileSize=$fileSize"
    logger.info(s"upload-s3 started: $fileInfo")
    val uploadTime = metricUploadCompleted.time()
    val request =
      PutObjectRequest.builder()
        .bucket(bucketName)
        .key(key.value)
        .serverSideEncryption(ServerSideEncryption.AWS_KMS)
        .contentLength(fileSize)
    contentType.foreach(request.contentType)
    contentMd5.foreach(request.contentMD5)

    Future
      .apply:
        s3Client.putObject(
          request.build(),
          RequestBody.fromInputStream(file, fileSize)
        )
      .map: res =>
        uploadTime.stop()
        metricUploadCompletedSize.inc(fileSize)
        logger.info(s"upload-s3 completed: $fileInfo")
        res
      .recoverWith: ex =>
        metricUploadFailed.mark()
        logger.error(s"upload-s3 error: transfer failed: ${ex.getMessage}", ex)
        Future.failed(ex)

  override def listFilesInBucket(bucketName: String): Source[Seq[S3Object], NotUsed] =
    Logger(getClass).info(s"Listing objects from bucket $bucketName")
    val request =
      ListObjectsV2Request
        .builder()
        .bucket(bucketName)
        .build()
    val responses = s3Client.listObjectsV2Paginator(request)

    Source.fromIterator(() => responses.iterator().asScala.map(_.contents.asScala.toSeq))

  override def copyFromQtoT(key: S3KeyName, versionId: String): Try[CopyObjectResponse] =
    Try {
      logger.info(s"Copying a file key ${key.value} and version: $versionId")
      metricCopyFromQtoT.mark()
      val copyRequest =
        CopyObjectRequest
          .builder()
          .sourceBucket(awsConfig.quarantineBucketName)
          .sourceKey(key.value)
          .sourceVersionId(versionId)
          .destinationBucket(awsConfig.transientBucketName)
          .destinationKey(key.value)
          .serverSideEncryption(ServerSideEncryption.AWS_KMS)
          .build()
      s3Client.copyObject(copyRequest)
    }

  override def getBucketProperties(bucketName: String): JsObject =
    val versioningStatus = s3Client.getBucketVersioning(GetBucketVersioningRequest.builder().bucket(bucketName).build()).statusAsString
    Json.obj("versioningStatus" -> versioningStatus)

  override def deleteObjectFromBucket(bucketName: String, key: S3KeyName): Unit =
    val versions = s3Client.listObjectVersions(ListObjectVersionsRequest.builder().bucket(bucketName).prefix(key.value).build()).versions.asScala

    val objectDescription = ReflectionToStringBuilder.toString(getObjectMetadata(bucketName, key, None))

    logger.info(s"Deleting object. Object ${key.value} from bucket $bucketName has ${versions.size} versions. Description: $objectDescription")

    versions.foreach: version =>
      val outcome = Try(s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(version.key).versionId(version.versionId).build()))
      logger.info(s"Outcome of deleting ${key.value} / ${version.versionId}: $outcome")

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
       key          =  S3Key.forZipSubdir(awsConfig.zipSubdir)(fileName)
       uploadResult <- uploadFile(
                         bucketName  = awsConfig.transientBucketName,
                         key         = key,
                         file        = java.io.FileInputStream(tempFile.path.toFile),
                         fileSize    = fileSize,
                         contentType = Some("application/zip"),
                         contentMd5  = Some(md5Hash)
                       )
       _            =  logger.debug(s"presigning $envelopeId")
       url          =  presign(
                         bucketName         = awsConfig.transientBucketName,
                         key                = key.value,
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

  private val presigner =
    S3Presigner.builder()
       // The creds on the s3client are not propaged - it tries to look them up and if not found
       // will use temporary creds (which expect a Security-Token to be included in the request).
       // Explicitly set so this doesn't happen.
      .credentialsProvider(StaticCredentialsProvider.create(credentials))
      .build()

  def presign(bucketName: String, key: String, expirationDuration: Duration): URL =
    val presignRequest =
      GetObjectPresignRequest.builder()
        .signatureDuration(java.time.Duration.ofSeconds(expirationDuration.toSeconds))
        .getObjectRequest:
          GetObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build()
        .build()

    presigner
      .presignGetObject(presignRequest)
      .url

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
