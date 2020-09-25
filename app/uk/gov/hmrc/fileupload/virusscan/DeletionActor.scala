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

package uk.gov.hmrc.fileupload.virusscan

import akka.actor.{Actor, ActorRef, Props}
import play.api.Logger
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}
import uk.gov.hmrc.fileupload.notifier.MarkFileAsInfected
import uk.gov.hmrc.fileupload.s3.{S3KeyName, S3Service}

import scala.concurrent.ExecutionContext

class DeletionActor(subscribe: (ActorRef, Class[_]) => Boolean,
                    deleteObjectFromQuarantineBucket: S3Service.DeleteFileFromQuarantineBucket,
                    createS3Key: (EnvelopeId, FileId) => S3KeyName)
                   (implicit executionContext: ExecutionContext) extends Actor {


  override def preStart = {
    subscribe(self, classOf[MarkFileAsInfected])
  }

  override def receive: Receive = {
    case e: MarkFileAsInfected =>
      Logger.info(s"MarkFileAsInfected received for envelopeId: ${e.id} and fileId: ${e.fileId} and version: ${e.fileRefId}")
      deleteInfectedFile(e)
  }

  private def deleteInfectedFile(infectedFile: MarkFileAsInfected): Unit = {
    val key = createS3Key(infectedFile.id, infectedFile.fileId)
    deleteObjectFromQuarantineBucket(key)
  }
}

object DeletionActor {
  def props(subscribe: (ActorRef, Class[_]) => Boolean,
            deleteObjectFromQuarantineBucket: S3Service.DeleteFileFromQuarantineBucket,
            createS3Key: (EnvelopeId, FileId) => S3KeyName)
           (implicit executionContext: ExecutionContext) =
    Props(new DeletionActor(subscribe, deleteObjectFromQuarantineBucket, createS3Key))
}
