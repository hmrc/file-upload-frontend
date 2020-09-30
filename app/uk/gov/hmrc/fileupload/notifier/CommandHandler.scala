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

package uk.gov.hmrc.fileupload.notifier

import play.api.Logger
import play.api.http.Status
import play.api.libs.json.{Json, Writes}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import uk.gov.hmrc.fileupload.infrastructure.PlayHttp.PlayHttpError
import uk.gov.hmrc.fileupload.notifier.NotifierRepository.NotificationFailedError
import uk.gov.hmrc.fileupload.notifier.NotifierService.{NotifyError, NotifyResult, NotifySuccess}

import scala.concurrent.{ExecutionContext, Future}

trait CommandHandler {
  def notify(command: AnyRef)(implicit ec: ExecutionContext): Future[NotifyResult]
}

class CommandHandlerImpl(httpCall: WSRequest => Future[Either[PlayHttpError, WSResponse]],
                     baseUrl: String, wsClient: WSClient, publish: AnyRef => Unit) extends CommandHandler {

  private val logger = Logger(getClass)

  val userAgent = "User-Agent" -> "FU-frontend-CH"

  def sendBackendCommand[T <: BackendCommand : Writes](command: T)(implicit ec: ExecutionContext): Future[NotifierRepository.Result] =
    httpCall(
      wsClient
        .url(s"$baseUrl/file-upload/commands/${ command.commandType }")
        .withBody(Json.toJson(command))
        .withMethod("POST")
        .withHttpHeaders(userAgent)
    ).map {
      case Left(error) => Left(NotificationFailedError(command.id, command.fileId, 500, error.message))
      case Right(response) => response.status match {
        case Status.OK => Right(command.id)
        case _ => Left(NotificationFailedError(command.id, command.fileId, response.status, response.body))
      }
    }

  private def sendNotification[T <: BackendCommand : Writes](c: T)
                              (implicit executionContext: ExecutionContext): Future[NotifierService.NotifyResult] =
    sendBackendCommand(c).map {
      case Right(_) => Right(NotifySuccess)
      case Left(e) =>
        logger.warn(s"Sending command to File Upload Backend failed ${e.statusCode} ${e.reason} $c")
        Left(NotifyError(e.statusCode, e.reason))
    }

  val notifySuccess = Right(NotifySuccess)

  def notify(command: AnyRef)(implicit ec: ExecutionContext): Future[NotifyResult] = {

    def sendCommandToBackendAndPublish[T <: BackendCommand : Writes](backendCommand: T) = {
      val result = sendNotification(backendCommand)
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
        Future.successful(notifySuccess)
    }
  }
}
