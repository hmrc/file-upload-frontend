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

package uk.gov.hmrc.fileupload.virusscan

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import cats.data.Xor
import uk.gov.hmrc.fileupload.FileReferenceId
import uk.gov.hmrc.fileupload.quarantine.Quarantined
import uk.gov.hmrc.fileupload.virusscan.ScanningService._

import scala.concurrent.{ExecutionContext, Future}

class ScannerActor(subscribe: (ActorRef, Class[_]) => Boolean,
                   scanBinaryData: (FileReferenceId) => Future[ScanResult],
                   publish: (AnyRef) => Unit)
                  (implicit executionContext: ExecutionContext) extends Actor with ActorLogging {

  override def preStart = {
    subscribe(self, classOf[Quarantined])
  }

  def receive = {
    case e: Quarantined =>
      log.info("Quarantined event received for {} and {}", e.envelopeId, e.fileId)
      scanBinaryData(e.fileReferenceId).map {
        case Xor.Right(ScanResultFileClean) => publish(NoVirusDetected(e.fileReferenceId, e.envelopeId, e.fileId))
        case Xor.Left(ScanResultVirusDetected) => publish(VirusDetected(e.fileReferenceId, e.envelopeId, e.fileId, "virus detected"))
        case Xor.Left(ScanResultFailureSendingChunks(t)) =>
        case Xor.Left(ScanResultUnexpectedResult) =>
        case Xor.Left(ScanResultError(t)) =>
      }
    case _ =>
  }
}

object ScannerActor {

  def props(subscribe: (ActorRef, Class[_]) => Boolean, scanBinaryData: (FileReferenceId) => Future[ScanResult], publish: (AnyRef) => Unit)
           (implicit executionContext: ExecutionContext) =
    Props(new ScannerActor(subscribe = subscribe, scanBinaryData = scanBinaryData, publish = publish))
}
