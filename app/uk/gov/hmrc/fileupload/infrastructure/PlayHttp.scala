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

package uk.gov.hmrc.fileupload.infrastructure

import java.net.URL

import play.api.libs.ws.{WSRequestHolder, WSResponse}
import play.api.mvc.Headers
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{DataEvent, EventTypes}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

object PlayHttp {

  def auditedExecute(connector: AuditConnector, appName: String)(request: WSRequestHolder)(implicit ec: ExecutionContext): Future[WSResponse] = {
    val hc = headerCarrier(request)
    val eventualResponse = request.execute()
    eventualResponse.foreach {
      response => {
        val path = new URL(request.url).getPath
        connector.sendEvent(DataEvent(appName, EventTypes.Succeeded,
          tags = Map("method" -> request.method, "statusCode" -> s"${response.status}", "responseBody" -> response.body)
            ++ hc.toAuditTags(path, path),
          detail = hc.toAuditDetails()))
      }
    }
    eventualResponse
  }

  private def headerCarrier(request: WSRequestHolder): HeaderCarrier = {
    HeaderCarrier.fromHeadersAndSession(new Headers {
      override protected val data: Seq[(String, Seq[String])] = request.headers.toSeq
    })
  }
}
