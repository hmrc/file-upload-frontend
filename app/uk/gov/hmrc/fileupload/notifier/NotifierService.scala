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

package uk.gov.hmrc.fileupload.notifier

import cats.data.Xor
import play.api.Logger
import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.notifier.NotifierRepository.Notification
import uk.gov.hmrc.fileupload.quarantine.FileInQuarantineStored
import uk.gov.hmrc.fileupload.virusscan.FileScanned

import scala.concurrent.{ExecutionContext, Future}

object NotifierService {

  type NotifyResult = Xor[NotifyError, NotifySuccess.type]

  case object NotifySuccess

  case class NotifyError(statusCode: Int, reason: String)

  val notifySuccess = Xor.right(NotifySuccess)

  def notify(send: Notification => Future[NotifierRepository.Result], publish: AnyRef => Unit)
            (event: AnyRef)
            (implicit executionContext: ExecutionContext): Future[NotifyResult] = {
    event match {
      case e: FileInQuarantineStored =>
        val result = sendNotification(send, Notification(e.envelopeId, e.fileId, e.getClass.getSimpleName, Json.toJson(e)))
        result.map(r => r.foreach(_ => publish(event)))
        result
      case e: FileScanned =>
        val result = sendNotification(send, Notification(e.envelopeId, e.fileId, e.getClass.getSimpleName, Json.toJson(e)))
        result.map(r => r.foreach(_ => publish(event)))
        result
      case _ =>
        publish(event)
        Future.successful(notifySuccess)
    }
  }

  private def sendNotification(send: Notification => Future[NotifierRepository.Result], notification: Notification)
                              (implicit executionContext: ExecutionContext): Future[NotifyResult] =
    send(notification).map {
      case Xor.Right(_) => Xor.right(NotifySuccess)
      case Xor.Left(e) =>
        Logger.warn(s"Sending event to external system failed ${e.statusCode} ${e.reason} ${notification}")
        Xor.left(NotifyError(e.statusCode, e.reason))
    }


}
