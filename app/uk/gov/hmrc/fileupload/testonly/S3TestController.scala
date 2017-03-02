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

package uk.gov.hmrc.fileupload.testonly

import java.io.{File, FileOutputStream, InputStream, OutputStreamWriter}

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{GetObjectRequest, ObjectMetadata, PutObjectRequest}
import com.typesafe.config.ConfigFactory
import play.api.mvc.{Action, Controller}

import scala.collection.JavaConverters._

trait S3TestController { self: Controller =>
  val awsClient = new AwsDummyClient()

  def listFilesInQuarantine() = Action {
    Ok(awsClient.listBucket(awsClient.quarantineBucket))
  }

  def listFilesInTransient() = Action {
    Ok(awsClient.listBucket(awsClient.transientBucket))
  }

  def uploadTestFileQuarantine() = Action {
    Ok(awsClient.uploadFile(awsClient.quarantineBucket))
  }

  def uploadTestFileTransient() = Action {
    Ok(awsClient.uploadFile(awsClient.transientBucket))
  }
}

class AwsConfig {
  protected val config = ConfigFactory.load()
  def fileUploadBucketQuarantine: String = config.getString("aws.s3.bucket.upload.quarantine")
  def fileUploadBucketTransient: String = config.getString("aws.s3.bucket.upload.transient")
  def accessKeyId: String = config.getString("aws.access.key.id")
  def secretAccessKey: String = config.getString("aws.secret.access.key")
}

class AwsDummyClient() {
  val awsConfig = new AwsConfig()
  val credentials = new BasicAWSCredentials(awsConfig.accessKeyId, awsConfig.secretAccessKey)
  val s3 = new AmazonS3Client(credentials)
  val londonRegion = Region.getRegion(Regions.EU_WEST_2)
  s3.setRegion(londonRegion)

  val quarantineBucket = awsConfig.fileUploadBucketQuarantine
  val transientBucket = awsConfig.fileUploadBucketTransient

  val key = "MyObjectKey"

  def getBuckets() = s3.listBuckets.asScala.map(_.getName).mkString("\n")

  def listBucket(bucketName: String): String = {
    var listing = s3.listObjects(bucketName)
    val summaries = listing.getObjectSummaries
    while (listing.isTruncated) {
      listing = s3.listNextBatchOfObjects(listing)
      summaries.addAll(listing.getObjectSummaries)
    }
    "files in the basket are: \n" + summaries.asScala.map(_.getKey).mkString("\n")
  }

  def uploadFile(bucketName: String) = {
    val putRequest = new PutObjectRequest(bucketName, key, S3FileUtils.createSampleFile)
    val objectMetadata = new ObjectMetadata()
    objectMetadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION)
    putRequest.setMetadata(objectMetadata)

    s3.putObject(putRequest)
    val o = s3.getObject(new GetObjectRequest(bucketName, key))
    S3FileUtils.displayTextInputStream(o.getObjectContent)
  }


}

object S3FileUtils {
  def createSampleFile: File = {
    val file = File.createTempFile("aws-java-sdk-", ".txt")
    file.deleteOnExit()
    val writer = new OutputStreamWriter(new FileOutputStream(file))
    writer.write("Sample file uploaded\n")
    writer.close()
    file
  }

  def displayTextInputStream(input: InputStream): String = {
    scala.io.Source.fromInputStream(input).mkString
  }
}
