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

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import cats.data.Xor
import play.api.mvc.Request
import uk.gov.hmrc.fileupload.FileReferenceId
import uk.gov.hmrc.fileupload.upload.UploadService.{UploadResult, UploadServiceEnvelopeNotFoundError}
import uk.gov.hmrc.fileupload.virusscan.NoVirusDetected

import scala.concurrent.{ExecutionContext, Future}

class TransferActor(subscribe: (ActorRef, Class[_]) => Boolean,
                    uploadFile: (FileReferenceId, Request[_]) => Future[UploadResult],
                    publish: (AnyRef) => Unit)
                   (implicit executionContext: ExecutionContext) extends Actor with ActorLogging {

  override def preStart = {
    subscribe(self, classOf[NoVirusDetected])
  }

  def receive = {
    case e: NoVirusDetected =>
      log.info("No virus detected event received for {} and {}", e.envelopeId, e.fileId)
      uploadFile(e.fileReferenceId, null).map {
        case Xor.Right(envelopeId) => publish(ToTransientMoved(e.fileReferenceId, e.envelopeId, e.fileId))
        case Xor.Left(UploadServiceEnvelopeNotFoundError(id)) => publish(MovingToTransientFailed(e.fileReferenceId, e.envelopeId, e.fileId, reason = s"Envelope ${e.envelopeId} not found"))
        case Xor.Left(_) => publish(MovingToTransientFailed(e.fileReferenceId, e.envelopeId, e.fileId, reason = "unexpected error"))
      }
    case _ =>
  }
}

object TransferActor {

  def props(subscribe: (ActorRef, Class[_]) => Boolean, uploadFile: (FileReferenceId, Request[_]) => Future[UploadResult], publish: (AnyRef) => Unit)
           (implicit executionContext: ExecutionContext) =
    Props(new TransferActor(subscribe = subscribe, uploadFile = uploadFile, publish = publish))
}
