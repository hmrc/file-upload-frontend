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
import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.notifier.NotifierRepository.{Notification, Result}
import uk.gov.hmrc.fileupload.quarantine.FileInQuarantineStored
import uk.gov.hmrc.fileupload.virusscan.FileScanned

import scala.concurrent.{ExecutionContext, Future}

class NotifierActor(subscribe: (ActorRef, Class[_]) => Boolean,
                    notify: (Notification) => Future[Result])
                   (implicit executionContext: ExecutionContext) extends Actor with ActorLogging {
  
  override def preStart = {
    subscribe(self, classOf[FileInQuarantineStored])
    subscribe(self, classOf[FileScanned])
  }
  
  def receive = {
    case e: FileInQuarantineStored =>
      log.info("FileInQuarantineStored event received for {} and {}", e.envelopeId, e.fileId)
      notify(Notification(e.envelopeId, e.fileId, e.getClass.getSimpleName, Json.toJson(e)))
    case e: FileScanned =>
      log.info("FileScanned event received for {} and {} and hasVirus {}", e.envelopeId, e.fileId, e.hasVirus)
      notify(Notification(e.envelopeId, e.fileId, e.getClass.getSimpleName, Json.toJson(e)))
  }
}

object NotifierActor {

  def props(subscribe: (ActorRef, Class[_]) => Boolean,
            notify: (Notification) => Future[Result])
           (implicit executionContext: ExecutionContext) =
    Props(new NotifierActor(subscribe = subscribe, notify = notify))
}
