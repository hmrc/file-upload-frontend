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

import java.time.Instant

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.amazonaws.services.s3.model.S3ObjectSummary
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}

class OldFileHandler(s3Service: S3Service)(implicit ec :ExecutionContext) {

  implicit val system = ActorSystem("reactive-tweets")

  def purge() : Future[Unit] = {
    countFiles()
    purgeFiles()

    Future.successful()

  }

  private def countFiles() {
    implicit val materializer = ActorMaterializer()

    for (fileCount: Int <- s3Service.listFilesInTransient.runFold(0)((prev, page) => prev + page.size)) {
      Logger.info(s"There are $fileCount files in inbound bucket")
    }
  }

  private def purgeFiles(): Unit = {
    implicit val materializer = ActorMaterializer()
    s3Service.listFilesInTransient.runFold(0)((index, page) => purgePage(page, index))
  }


  private def purgePage(page: Seq[S3ObjectSummary], pageIndex : Int): Int =
    page.foldLeft(pageIndex)((index, file) => {
      if (isOld(file)) {
        Logger.info(s"Deleting file ($index) ${file.getKey}, where last modified date ${file.getLastModified}")
        s3Service.deleteObjectFromTransient(file.getKey)
      } else {
        Logger.info(s"File ($index) ${file.getKey} is not old: ${file.getLastModified}")
      }
      index + 1
    })


  private def isOld(s3ObjectSummary: S3ObjectSummary) : Boolean = {
    val now = Instant.now()
    val fileModifiedTime = s3ObjectSummary.getLastModified.toInstant
    val age = java.time.Duration.between(fileModifiedTime, now)
    age.compareTo(java.time.Duration.ofDays(31)) > 0
  }

}
