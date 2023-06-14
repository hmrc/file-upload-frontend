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

import java.io.InputStream
import java.net.URL

import akka.NotUsed
import akka.stream.{IOResult, Materializer}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.amazonaws.services.s3.model.{CopyObjectResult, S3ObjectSummary}
import com.amazonaws.services.s3.transfer.model.UploadResult
import play.api.libs.json.JsValue
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}
import uk.gov.hmrc.fileupload.quarantine.FileData
import uk.gov.hmrc.fileupload.s3.S3Service.{DeleteFileFromQuarantineBucket, DownloadFromBucket, StreamResult, UploadToQuarantine}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait S3Service {
  def awsConfig: AwsConfig

  def download(bucketName: String, key: S3KeyName): Option[StreamWithMetadata]

  def download(bucketName: String, key: S3KeyName, versionId: String): Option[StreamWithMetadata]

  def retrieveFileFromQuarantine(key: S3KeyName, versionId: String)(implicit ec: ExecutionContext): Future[Option[FileData]]

  def upload(bucketName: String, key: S3KeyName, file: InputStream, fileSize: Int): Future[UploadResult]

  def uploadToQuarantine: UploadToQuarantine =
    upload(awsConfig.quarantineBucketName, _, _, _)

  def downloadFromTransient: DownloadFromBucket =
    download(awsConfig.transientBucketName, _)

  def downloadFromQuarantine: DownloadFromBucket =
    download(awsConfig.quarantineBucketName, _)

  def listFilesInBucket(bucketName: String): Source[Seq[S3ObjectSummary], NotUsed]

  def listFilesInQuarantine: Source[Seq[S3ObjectSummary], NotUsed] =
    listFilesInBucket(awsConfig.quarantineBucketName)

  def listFilesInTransient: Source[Seq[S3ObjectSummary], NotUsed] =
    listFilesInBucket(awsConfig.transientBucketName)

  def copyFromQtoT(key: S3KeyName, versionId: String): Try[CopyObjectResult]

  def getFileLengthFromQuarantine(key: S3KeyName, versionId: String): Long

  def getBucketProperties(bucketName: String): JsValue

  def getQuarantineBucketProperties =
    getBucketProperties(awsConfig.quarantineBucketName)

  def getTransientBucketProperties =
    getBucketProperties(awsConfig.transientBucketName)

  def deleteObjectFromBucket(bucketName: String, key: S3KeyName): Unit

  def deleteObjectFromTransient: S3KeyName => Unit =
    deleteObjectFromBucket(awsConfig.transientBucketName, _)

  def deleteObjectFromQuarantine: DeleteFileFromQuarantineBucket =
    deleteObjectFromBucket(awsConfig.quarantineBucketName, _)

  def zipAndPresign(envelopeId: EnvelopeId, files: List[(FileId, Option[String])])(implicit ec : ExecutionContext, materializer: Materializer): Future[ZipData]
}

object S3Service {
  type StreamResult = Source[ByteString, Future[IOResult]]

  type UploadToQuarantine = (S3KeyName, InputStream, Int) => Future[UploadResult]

  type DownloadFromBucket = S3KeyName => Option[StreamWithMetadata]

  type DeleteFileFromQuarantineBucket = S3KeyName => Unit
}

case class Metadata(
                     contentType: String,
                     contentLength: Long,
                     versionId: String = "",
                     ETag: String = "",
                     s3Metadata: Option[Map[String, String]] = None)

case class StreamWithMetadata(stream: StreamResult, metadata: Metadata)


case class ZipData(
  name       : String,
  size       : Long,
  md5Checksum: String,
  url        : URL
)
object ZipData {
  import play.api.libs.json.__
  import play.api.libs.functional.syntax._
  val writes =
    ( (__ \ "name"       ).write[String]
    ~ (__ \ "size"       ).write[Long]
    ~ (__ \ "md5Checksum").write[String]
    ~ (__ \ "url"        ).write[String].contramap[URL](_.toString)
    )(unlift(ZipData.unapply))
}

class MissingFileException(message: String) extends RuntimeException(message)
