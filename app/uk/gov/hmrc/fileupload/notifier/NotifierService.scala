/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.libs.json.Writes

import scala.concurrent.{ExecutionContext, Future}

object NotifierService {

  type NotifyResult = Xor[NotifyError, NotifySuccess.type]

  case object NotifySuccess

  case class NotifyError(statusCode: Int, reason: String)

  val notifySuccess = Xor.right(NotifySuccess)

  def notify(send: BackendCommand => Future[NotifierRepository.Result], publish: AnyRef => Unit)
            (command: AnyRef)
            (implicit executionContext: ExecutionContext): Future[NotifyResult] = {

    def sendCommandToBackendAndPublish[T <: BackendCommand : Writes](backendCommand: T) = {
      val result = sendNotification(send, backendCommand)
      result.map(r => r.foreach(_ => publish(command)))
      result
    }

    command match {
      case c: QuarantineFile => sendCommandToBackendAndPublish(c)
      case c: MarkFileAsClean => sendCommandToBackendAndPublish(c)
      case c: MarkFileAsInfected => sendCommandToBackendAndPublish(c)
      case c: StoreFile => sendCommandToBackendAndPublish(c)
      case _ =>
        publish(command)
        Future.successful(notifySuccess)
    }
  }

  private def sendNotification(send: BackendCommand => Future[NotifierRepository.Result], c: BackendCommand)
                              (implicit executionContext: ExecutionContext): Future[NotifyResult] =
    send(c).map {
      case Xor.Right(_) => Xor.right(NotifySuccess)
      case Xor.Left(e) =>
        Logger.warn(s"Sending command to File Upload Backend failed ${e.statusCode} ${e.reason} $c")
        Xor.left(NotifyError(e.statusCode, e.reason))
    }


}
