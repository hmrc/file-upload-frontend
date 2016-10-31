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

import cats.data.Xor
import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{WS, WSRequest, WSResponse}
import uk.gov.hmrc.fileupload.infrastructure.PlayHttp.PlayHttpError
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

import scala.concurrent.{ExecutionContext, Future}

object NotifierRepository {

  type Result = Xor[NotificationError, EnvelopeId]

  case class Notification(envelopeId: EnvelopeId, fileId: FileId, eventType: String, event: JsValue)

  sealed trait NotificationError {
    def statusCode: Int

    def reason: String
  }

  case class NotificationFailedError(envelopeId: EnvelopeId,
                                     fileId: FileId,
                                     override val statusCode: Int,
                                     override val reason: String) extends NotificationError

  def send(httpCall: (WSRequest => Future[Xor[PlayHttpError, WSResponse]]), baseUrl: String)
          (notification: Notification)
          (implicit executionContext: ExecutionContext): Future[Result] =
    httpCall(WS.url(s"$baseUrl/file-upload/events/${notification.eventType}").withBody(Json.stringify(notification.event)).withMethod("POST")).map {
      case Xor.Left(error) => Xor.left(NotificationFailedError(notification.envelopeId, notification.fileId, 500, error.message))
      case Xor.Right(response) => response.status match {
        case Status.OK => Xor.right(notification.envelopeId)
        case _ => Xor.left(NotificationFailedError(notification.envelopeId, notification.fileId, response.status, response.body))
      }
    }
}
