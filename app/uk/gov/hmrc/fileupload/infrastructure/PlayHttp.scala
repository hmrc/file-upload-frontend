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

package uk.gov.hmrc.fileupload.infrastructure

import java.net.URL

import play.api.libs.ws.{WSRequest, WSResponse}
import play.api.mvc.Request
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.{DataEvent, EventTypes}
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object PlayHttp {

  def audit(
    connector  : AuditConnector,
    appName    : String,
    errorLogger: Option[(Throwable => Unit)]
  )(success: Boolean,
    status : Int,
    body   : String
  )(request: Request[_]
  )(implicit
    ec: ExecutionContext
  ): Future[AuditResult] = {
    val hc = HeaderCarrierConverter.fromRequest(request)

    connector.sendEvent(
      DataEvent(
        appName,
        if (success) EventTypes.Succeeded else EventTypes.Failed,
        tags   = Map(
                   "method"       -> request.method,
                   "statusCode"   -> status.toString,
                   "responseBody" -> ""
                 ) ++ hc.toAuditTags(request.path, request.path),
        detail = hc.toAuditDetails()
      )
    )
  }

  case class PlayHttpError(message: String)

  def execute(
    connector  : AuditConnector,
    appName    : String,
    errorLogger: Option[(Throwable => Unit)]
  )(request: WSRequest,
    hc     : HeaderCarrier
  )(implicit
    ec: ExecutionContext
  ): Future[Either[PlayHttpError, WSResponse]] = {
    val eventualResponse = request.execute()

    eventualResponse.foreach {
      response =>
        val path = new URL(request.url).getPath
        connector.sendEvent(
          DataEvent(
            appName,
            EventTypes.Succeeded,
            tags   = Map(
                       "method"       -> request.method,
                       "statusCode"   -> response.status.toString,
                       "responseBody" -> response.body
                     ) ++ hc.toAuditTags(path, path),
            detail = hc.toAuditDetails()
          )
        )
    }

    eventualResponse.map(Right.apply)
      .recover {
        case NonFatal(t) =>
          errorLogger.foreach(_.apply(t))
          Left(PlayHttpError(t.getMessage))
      }
  }
}
