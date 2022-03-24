/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.Logger
import play.api.libs.json.Writes

import scala.concurrent.{ExecutionContext, Future}

object NotifierService {

  type NotifyResult = Either[NotifyError, NotifySuccess.type]

  case object NotifySuccess

  case class NotifyError(statusCode: Int, reason: String)

  private val logger = Logger(getClass)

  def notify(send: BackendCommand => Future[CommandHandler.NotificationResult], publish: AnyRef => Unit)
            (command: AnyRef)
            (implicit executionContext: ExecutionContext): Future[NotifyResult] = {

    def sendCommandToBackendAndPublish[T <: BackendCommand : Writes](backendCommand: T) = {
      val result = sendNotification(send, backendCommand)
      result.map(_.right.foreach(_ => publish(command)))
      result
    }

    command match {
      case c: QuarantineFile => sendCommandToBackendAndPublish(c)
      case c: MarkFileAsClean => sendCommandToBackendAndPublish(c)
      case c: MarkFileAsInfected => sendCommandToBackendAndPublish(c)
      case c: StoreFile => sendCommandToBackendAndPublish(c)
      case _ =>
        publish(command)
        Future.successful(Right(NotifySuccess))
    }
  }

  private def sendNotification(send: BackendCommand => Future[CommandHandler.NotificationResult], c: BackendCommand)
                              (implicit executionContext: ExecutionContext): Future[NotifyResult] =
    send(c).map {
      case Right(_) => Right(NotifySuccess)
      case Left(e) =>
        logger.warn(s"Sending command to File Upload Backend failed ${e.statusCode} ${e.reason} $c")
        Left(NotifyError(e.statusCode, e.reason))
    }
}
