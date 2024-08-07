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

package uk.gov.hmrc.fileupload.notifier

import com.typesafe.config.ConfigFactory
import play.api.Logger
import play.api.http.Status
import play.api.libs.json.{Json, Writes}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse, writeableOf_JsValue}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}
import uk.gov.hmrc.fileupload.infrastructure.PlayHttp.PlayHttpError
import uk.gov.hmrc.fileupload.notifier.NotifierService.{NotifyError, NotifyResult, NotifySuccess}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait CommandHandler {
  def notify(command: AnyRef)(using ExecutionContext, HeaderCarrier): Future[NotifyResult]
}

object CommandHandler {

  type NotificationResult = Either[NotificationError, EnvelopeId]

  enum NotificationError(
    val statusCode: Int,
    val reason    : String
  ):
    case NotificationFailedError(
      envelopeId: EnvelopeId,
      fileId    : FileId,
      override val statusCode: Int,
      override val reason    : String
    ) extends NotificationError(statusCode, reason)
}

class CommandHandlerImpl(
  auditedHttpCall: (WSRequest, HeaderCarrier) => Future[Either[PlayHttpError, WSResponse]],
  baseUrl        : String,
  wsClient       : WSClient,
  publish        : AnyRef => Unit
) extends CommandHandler:
  import CommandHandler._

  private val logger = Logger(getClass)

  val userAgent = "User-Agent" -> "FU-frontend-CH"

  private val hcConfig = HeaderCarrier.Config.fromConfig(ConfigFactory.load())

  def sendBackendCommand[T <: BackendCommand : Writes](
    command: T
  )(using
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[NotificationResult] = {
    val url = s"$baseUrl/file-upload/commands/${command.commandType}"
    auditedHttpCall(
      wsClient
        .url(url)
        .withBody(Json.toJson(command))
        .withMethod("POST")
        .withHttpHeaders(userAgent +: hc.headersForUrl(hcConfig)(url) :_ *),
      hc
    ).map:
      case Left(error)     =>
        Left(NotificationError.NotificationFailedError(command.id, command.fileId, 500, error.message))
      case Right(response) =>
        response.status match
          case Status.OK => Right(command.id)
          case _         => Left(NotificationError.NotificationFailedError(command.id, command.fileId, response.status, response.body))
  }

  private def sendNotification[T <: BackendCommand : Writes](
    c: T
  )(using
    ExecutionContext,
    HeaderCarrier
  ): Future[NotifierService.NotifyResult] =
    sendBackendCommand(c).map:
      case Right(_) =>
        Right(NotifySuccess)
      case Left(e)  =>
        logger.warn(s"Sending command to File Upload Backend failed ${e.statusCode} ${e.reason} $c")
        Left(NotifyError(e.statusCode, e.reason))

  val notifySuccess = Right(NotifySuccess)

  def notify(command: AnyRef)(using ExecutionContext, HeaderCarrier): Future[NotifyResult] =
    def sendCommandToBackendAndPublish[T <: BackendCommand : Writes](backendCommand: T) =
      val result = sendNotification(backendCommand)
      result.map(_.foreach(_ => publish(command)))
      result

    command match
      case c: QuarantineFile     => sendCommandToBackendAndPublish(c)
      case c: MarkFileAsClean    => sendCommandToBackendAndPublish(c)
      case c: MarkFileAsInfected => sendCommandToBackendAndPublish(c)
      case c: StoreFile          => sendCommandToBackendAndPublish(c)
      case _                     => publish(command)
                                    Future.successful(notifySuccess)

end CommandHandlerImpl
