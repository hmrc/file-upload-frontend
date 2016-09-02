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

package uk.gov.hmrc.fileupload.testonly

import play.api.Play.current
import play.api.libs.json.Json
import play.api.libs.ws.{WS, WSResponse}
import play.api.mvc.Action
import play.api.mvc.Results._
import uk.gov.hmrc.fileupload.quarantine.Repository

import scala.concurrent.ExecutionContext

class TestOnlyController(baseUrl: String, quarantineRepo: Repository)(implicit executionContext: ExecutionContext) {

  def createEnvelope() = Action.async { request =>
    def extractEnvelopeId(response: WSResponse): String =
      response
        .allHeaders
        .get("Location")
        .flatMap(_.headOption)
        .map( l => l.substring(l.lastIndexOf("/") + 1) )
        .getOrElse("missing/invalid")

    val payload = Json.obj()

    WS.url(s"$baseUrl/file-upload/envelope").post(payload).map { response =>
      Created(Json.obj("envelopeId" -> extractEnvelopeId(response)))
    }
  }

  def downloadFile(envelopeId: String, fileId: String) = Action.async { request =>
    WS.url(s"$baseUrl/file-upload/envelope/$envelopeId/file/$fileId/content").getStream().map {
      case (headers, enumerator) => Ok.feed(enumerator).withHeaders(
        "Content-Length" -> headers.headers("Content-Length").head,
        "Content-Disposition" -> headers.headers("Content-Disposition").head)
    }
  }

  def transferGetEnvelopes() = Action.async { request =>
    WS.url(s"$baseUrl/file-transfer/envelopes").get().map { response =>
      Ok(Json.parse(response.body))
    }
  }

  def transferDownloadEnvelope(envelopeId: String) = Action.async { request =>
    WS.url(s"$baseUrl/file-transfer/envelopes/$envelopeId").getStream().map {
      case (headers, enumerator) => Ok.feed(enumerator).withHeaders(
        "Content-Type" -> headers.headers("Content-Type").head,
        "Content-Length" -> headers.headers("Content-Length").head)
    }
  }

  def transferDeleteEnvelope(envelopeId: String) = Action.async { request =>
    WS.url(s"$baseUrl/file-transfer/envelopes/$envelopeId").delete().map { response =>
      new Status(response.status)(response.body)
    }
  }

  def cleanup() = Action.async { request =>
    for {
      cleaningQuarantine  <- quarantineRepo.removeAll().map(_.forall(_.ok))
      cleaningTransient   <- WS.url(s"$baseUrl/file-upload/test-only/cleanup-transient").post(Json.obj()).map { _.status == 200 }
    } yield {
      if (cleaningQuarantine && cleaningTransient) {
        Ok
      } else {
        InternalServerError(s"cleaningQuarantine=$cleaningQuarantine, cleaningTransient=$cleaningTransient")
      }
    }
  }
}
