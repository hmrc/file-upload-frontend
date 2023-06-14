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

package uk.gov.hmrc.fileupload.transfer

import akka.actor.{Actor, ActorRef, Props}
import com.amazonaws.services.s3.model.CopyObjectResult
import play.api.Logger
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}
import uk.gov.hmrc.fileupload.notifier.{CommandHandler, MarkFileAsClean, StoreFile}
import uk.gov.hmrc.fileupload.s3.S3KeyName
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class TransferActor(subscribe: (ActorRef, Class[_]) => Boolean,
                    createS3Key: (EnvelopeId, FileId) => S3KeyName,
                    commandHandler: CommandHandler,
                    getFileLength: (EnvelopeId, FileId, FileRefId) => Long,
                    transferFile: (S3KeyName, String) => Try[CopyObjectResult])(implicit ec: ExecutionContext) extends Actor {

  private val logger = Logger(getClass)

  override def preStart(): Unit = {
    subscribe(self, classOf[MarkFileAsClean])
    subscribe(self, classOf[TransferRequested])
  }

  def receive = {
    case e: MarkFileAsClean =>
      logger.info(s"MarkFileAsClean received for envelopeId: ${e.id} and fileId: ${e.fileId} and version: ${e.fileRefId}")
      transfer(e.id, e.fileId, e.fileRefId)

    case e: TransferRequested =>
      logger.info(s"TransferRequested received for ${e.envelopeId} and ${e.fileId} and ${e.fileRefId}")
      transfer(e.envelopeId, e.fileId, e.fileRefId)
  }

  private def transfer(envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId): Unit =
    transferFile(createS3Key(envelopeId, fileId), fileRefId.value) match {
      case Success(_) =>
        implicit val hc = HeaderCarrier()
        commandHandler.notify(
          StoreFile(envelopeId, fileId, fileRefId, getFileLength(envelopeId, fileId, fileRefId))
        )
        logger.info(s"File successfully transferred for envelopeId: $envelopeId, fileId: $fileId and version: $fileRefId")
      case Failure(ex) =>
        logger.error(s"File not transferred for $envelopeId and $fileId and $fileRefId", ex)
    }
}

object TransferActor {

  def props(subscribe: (ActorRef, Class[_]) => Boolean,
            createS3Key: (EnvelopeId, FileId) => S3KeyName,
            commandHandler: CommandHandler,
            getFileLength: (EnvelopeId, FileId, FileRefId) => Long,
            transferFile: (S3KeyName, String) => Try[CopyObjectResult])(implicit ec: ExecutionContext) =
    Props(new TransferActor(subscribe, createS3Key, commandHandler, getFileLength, transferFile))
}
