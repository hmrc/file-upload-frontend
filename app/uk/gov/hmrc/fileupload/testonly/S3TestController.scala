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

import akka.stream.scaladsl.Source
import com.amazonaws.services.s3.transfer.model.UploadResult
import play.api.mvc.{Action, Controller}
import uk.gov.hmrc.fileupload.s3.InMemoryMultipartFileHandler.cacheFileInMemory
import uk.gov.hmrc.fileupload.s3.S3JavaSdkService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


trait S3TestController { self: Controller =>

  val s3Service = new S3JavaSdkService()
  import s3Service.awsConfig._

  def filesInQuarantine() = listFilesInBucket(quarantineBucketName)

  def filesInTransient() = listFilesInBucket(transientBucketName)

  def listFilesInBucket(bucketName: String) = Action {
    val header = Source.single(s"Files in bucket `$bucketName` are: \n")
    val files =
      s3Service.listFilesInBucket(bucketName)
        .map(summaries => summaries.map(_.getKey))
        .map(_.mkString("\n"))

    Ok.chunked(header.concat(files))
  }

  def uploadFile(fileName: String) = Action.async(parse.multipartFormData(cacheFileInMemory)) { req =>
    val numberOfFiles = req.body.files.size
    if (numberOfFiles == 1) {
      val uploadedFile = req.body.files.head.ref

      def formatResult(ur: UploadResult) = {
        import ur._
        s"key $getKey, version: $getVersionId, eTag: $getETag"
      }

      s3Service.upload(quarantineBucketName, fileName, uploadedFile.inputStream, uploadedFile.size)
        .map(r => Ok(formatResult(r)))
    } else {
     Future.successful(BadRequest("Expected exactly one file to be attached"))
    }
  }

  def s3downloadFile(fileName: String) = Action { req =>
    val download = s3Service.download(quarantineBucketName, fileName)
    Ok.chunked(download)
  }

}