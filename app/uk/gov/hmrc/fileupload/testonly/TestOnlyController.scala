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

package uk.gov.hmrc.fileupload.testonly

import play.api.Play.current
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{WS, WSResponse}
import play.api.mvc.{Action, Controller}

import scala.concurrent.ExecutionContext

class TestOnlyController(baseUrl: String, recreateCollections: () => Unit)(implicit executionContext: ExecutionContext) extends Controller {

  def createEnvelope() = Action.async { request =>
    def extractEnvelopeId(response: WSResponse): String =
      response
        .allHeaders
        .get("Location")
        .flatMap(_.headOption)
        .map(l => l.substring(l.lastIndexOf("/") + 1))
        .getOrElse("missing/invalid")

    val callback = request.queryString.get("callbackUrl").flatMap(_.headOption)
    val payload = Json.obj("callbackUrl" -> callback)

    WS.url(s"$baseUrl/file-upload/envelopes").post(payload).map { response =>
      Created(Json.obj("envelopeId" -> extractEnvelopeId(response)))
    }
  }

  def getEnvelope(envelopeId: String) = Action.async { request =>
    WS.url(s"$baseUrl/file-upload/envelopes/$envelopeId").get().map { response =>
      new Status(response.status)(response.body).withHeaders(
        "Content-Type" -> response.allHeaders("Content-Type").head
      )
    }
  }

  def downloadFile(envelopeId: String, fileId: String) = Action.async { request =>
    WS.url(s"$baseUrl/file-upload/envelopes/$envelopeId/files/$fileId/content").getStream().map {
      case (headers, enumerator) => Ok.feed(enumerator).withHeaders(
        "Content-Length" -> headers.headers("Content-Length").head,
        "Content-Disposition" -> headers.headers("Content-Disposition").head)
    }
  }

  def routingRequests() = Action.async(parse.json) { request =>
    WS.url(s"$baseUrl/file-routing/requests").post(request.body).map { response =>
      new Status(response.status)(response.body)
    }
  }

  def transferGetEnvelopes() = Action.async { request =>
    WS.url(s"$baseUrl/file-transfer/envelopes").get().map { response =>
      Ok(Json.parse(response.body))
    }
  }

  def transferDownloadEnvelope(envelopeId: String) = Action.async { request =>
    WS.url(s"$baseUrl/file-transfer/envelopes/$envelopeId").getStream().map {
      case (headers, enumerator) => Ok.chunked(enumerator).withHeaders(
        CONTENT_TYPE -> headers.headers(CONTENT_TYPE).headOption.getOrElse("unknown"),
        CONTENT_DISPOSITION -> headers.headers(CONTENT_DISPOSITION).headOption.getOrElse("unknown"))
    }
  }

  def transferDeleteEnvelope(envelopeId: String) = Action.async { request =>
    WS.url(s"$baseUrl/file-transfer/envelopes/$envelopeId").delete().map { response =>
      new Status(response.status)(response.body)
    }
  }

  def getEvents(streamId: String) = Action.async { request =>
    WS.url(s"$baseUrl/file-upload/events/$streamId").get().map { response =>
      new Status(response.status)(response.body).withHeaders {
        "Content-Type" -> response.allHeaders("Content-Type").head
      }
    }
  }

  def connDeathWatch(addr: String): Enumeratee[JsValue, JsValue] =
    Enumeratee.onIterateeDone { () => println(addr + " - SSE disconnected") }

  def filesInProgress() = Action.async { request =>
    WS.url(s"$baseUrl/file-upload/files/inprogress").get().map { response =>
      Ok(Json.parse(response.body))
    }
  }

  def recreateAllCollections() = Action.async {
    recreateCollections()
    WS.url(s"$baseUrl/file-upload/test-only/recreate-collections").post(Json.obj()).map {
      response => new Status(response.status)(response.body)
    }
  }

}
