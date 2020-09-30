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

import play.api.http.Status
import play.api.libs.json.JsValue
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import uk.gov.hmrc.fileupload.infrastructure.PlayHttp.PlayHttpError
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

import scala.concurrent.{ExecutionContext, Future}

object NotifierRepository {

  type Result = Either[NotificationError, EnvelopeId]

  case class Notification(envelopeId: EnvelopeId, fileId: FileId, eventType: String, event: JsValue)

  sealed trait NotificationError {
    def statusCode: Int

    def reason: String
  }

  case class NotificationFailedError(envelopeId: EnvelopeId,
                                     fileId: FileId,
                                     override val statusCode: Int,
                                     override val reason: String) extends NotificationError
  val userAgent = "User-Agent" -> "FU-frontend-notifier"

  def send(httpCall: (WSRequest => Future[Either[PlayHttpError, WSResponse]]), baseUrl: String, wsClient: WSClient)
          (notification: Notification)
          (implicit executionContext: ExecutionContext): Future[Result] =
    httpCall(
      wsClient
        .url(s"$baseUrl/file-upload/commands/${ notification.eventType }")
        .withBody(notification.event)
        .withMethod("POST")
        .withHttpHeaders(userAgent)
    ).map {
      case Left(error) => Left(NotificationFailedError(notification.envelopeId, notification.fileId, 500, error.message))
      case Right(response) => response.status match {
        case Status.OK => Right(notification.envelopeId)
        case _ => Left(NotificationFailedError(notification.envelopeId, notification.fileId, response.status, response.body))
      }
    }
}
