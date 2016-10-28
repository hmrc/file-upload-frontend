/*
 * Copyright 2016 HM Revenue & Customs
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
import cats.data.Xor
import play.api.Logger
import uk.gov.hmrc.fileupload.transfer.TransferService.{TransferResult, TransferServiceError}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}
import uk.gov.hmrc.fileupload.virusscan.FileScanned

import scala.concurrent.{ExecutionContext, Future}

class TransferActor(subscribe: (ActorRef, Class[_]) => Boolean,
                    transferFile: (EnvelopeId, FileId, FileRefId) => Future[TransferResult])
                   (implicit executionContext: ExecutionContext) extends Actor {

  override def preStart = {
    subscribe(self, classOf[FileScanned])
    subscribe(self, classOf[TransferRequested])
  }

  def receive = {
    case e: FileScanned if !e.hasVirus =>
      Logger.info(s"FileScanned received for ${e.envelopeId} and ${e.fileId} and ${e.fileRefId}")
      transfer(e.envelopeId, e.fileId, e.fileRefId)
    case e: TransferRequested =>
      Logger.info(s"TransferRequested received for ${e.envelopeId} and ${e.fileId} and ${e.fileRefId}")
      transfer(e.envelopeId, e.fileId, e.fileRefId)
    case _ =>
  }
  
  private def transfer(envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId): Unit =
    transferFile(envelopeId, fileId, fileRefId).map {
      case Xor.Right(envelopeId) =>
        Logger.info(s"File successful transferred $envelopeId and $fileId and $fileRefId")
      case Xor.Left(TransferServiceError(id, m)) =>
        Logger.info(s"File not transferred for $envelopeId and $fileId and $fileRefId with $m")
      case Xor.Left(_) =>
        Logger.info(s"File not transferred for $envelopeId and $fileId and $fileRefId")
    }
}

object TransferActor {

  def props(subscribe: (ActorRef, Class[_]) => Boolean, transferFile: (EnvelopeId, FileId, FileRefId) => Future[TransferResult])
           (implicit executionContext: ExecutionContext) =
    Props(new TransferActor(subscribe = subscribe, transferFile = transferFile))
}
