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

package uk.gov.hmrc.fileupload.notifier

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import cats.data.Xor
import uk.gov.hmrc.fileupload.{EnvelopeCallback, EnvelopeId}
import uk.gov.hmrc.fileupload.notifier.NotifierRepository.{Notification, NotifyResult}
import uk.gov.hmrc.fileupload.quarantine.Quarantined
import uk.gov.hmrc.fileupload.transfer.Service._
import uk.gov.hmrc.fileupload.transfer.{MovingToTransientFailed, ToTransientMoved}
import uk.gov.hmrc.fileupload.virusscan.{NoVirusDetected, VirusDetected}

import scala.concurrent.{ExecutionContext, Future}

class NotifierActor(subscribe: (ActorRef, Class[_]) => Boolean,
                    envelopeCallback: (EnvelopeId) => Future[EnvelopeCallbackResult],
                    notify: (Notification, EnvelopeCallback) => Future[NotifyResult])
                   (implicit executionContext: ExecutionContext) extends Actor with ActorLogging {
  
  override def preStart = {
    subscribe(self, classOf[Quarantined])
    subscribe(self, classOf[NoVirusDetected])
    subscribe(self, classOf[VirusDetected])
    subscribe(self, classOf[ToTransientMoved])
    subscribe(self, classOf[MovingToTransientFailed])
  }
  
  def receive = {
    case e: Quarantined =>
      log.info("Quarantined event received for {} and {}", e.envelopeId, e.fileId)
      notifyEnvelopeCallback(Notification(e.envelopeId, e.fileId, "QUARANTINED", None))
    case e: NoVirusDetected =>
      log.info("NoVirusDetected event received for {} and {}", e.envelopeId, e.fileId)
      notifyEnvelopeCallback(Notification(e.envelopeId, e.fileId, "CLEANED", None))
    case e: VirusDetected =>
      log.info("VirusDetected event received for {} and {} and reason = {}", e.envelopeId, e.fileId, e.reason)
      notifyEnvelopeCallback(Notification(e.envelopeId, e.fileId, "ERROR", Some("VirusDetected")))
    case e: ToTransientMoved =>
      log.info("ToTransientMoved event received for {} and {}", e.envelopeId, e.fileId)
      notifyEnvelopeCallback(Notification(e.envelopeId, e.fileId, "AVAILABLE", None))
    case e: MovingToTransientFailed =>
      log.info("MovingToTransientFailed event received for {} and {} and {}", e.envelopeId, e.fileId, e.reason)
      notifyEnvelopeCallback(Notification(e.envelopeId, e.fileId, "ERROR", Some("MovingToTransientFailed")))
  }

  def notifyEnvelopeCallback(notification: Notification) =
    envelopeCallback(notification.envelopeId).map {
      case Xor.Right(callback) => notify(notification, callback)
      case Xor.Left(e) => log.error(e.toString)
    }
}

object NotifierActor {

  def props(subscribe: (ActorRef, Class[_]) => Boolean,
            envelopeCallback: (EnvelopeId) => Future[EnvelopeCallbackResult],
            notify: (Notification, EnvelopeCallback) => Future[NotifyResult])
           (implicit executionContext: ExecutionContext) =
    Props(new NotifierActor(subscribe = subscribe, envelopeCallback = envelopeCallback, notify = notify))
}
