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

package uk.gov.hmrc.fileupload.infrastructure

import java.io.OutputStream
import java.net.{HttpURLConnection, URL}

import cats.data.Xor
import play.api.libs.iteratee.{Done, Input, Iteratee, Step}
import play.api.libs.ws.{WSRequestHolder, WSResponse}
import play.api.mvc.{Request, Headers}
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.{AuditResult, AuditConnector}
import uk.gov.hmrc.play.audit.model.{DataEvent, EventTypes}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object PlayHttp {

  def audit(connector: AuditConnector, appName: String, errorLogger: Option[(Throwable => Unit)])
           (success: Boolean, status: Int, body: String)
           (request: Request[_])
           (implicit ec: ExecutionContext): Future[AuditResult] = {

    val hc = HeaderCarrier.fromHeadersAndSession(new Headers {
      override protected val data: Seq[(String, Seq[String])] = request.headers.toMap.toSeq
    })

    connector.sendEvent(
      DataEvent(appName, if (success) EventTypes.Succeeded else EventTypes.Failed,
        tags = Map("method" -> request.method, "statusCode" -> s"${ status }", "responseBody" -> "")
          ++ hc.toAuditTags(request.path, request.path),
        detail = hc.toAuditDetails())
    )
  }

  case class PlayHttpError(message: String)

  def execute(connector: AuditConnector, appName: String, errorLogger: Option[(Throwable => Unit)])(request: WSRequestHolder)
             (implicit ec: ExecutionContext): Future[Xor[PlayHttpError, WSResponse]] = {
    val hc = headerCarrier(request)
    val eventualResponse = request.execute()

    eventualResponse.foreach {
      response => {
        val path = new URL(request.url).getPath
        connector.sendEvent(DataEvent(appName, EventTypes.Succeeded,
          tags = Map("method" -> request.method, "statusCode" -> s"${ response.status }", "responseBody" -> response.body)
            ++ hc.toAuditTags(path, path),
          detail = hc.toAuditDetails()))
      }
    }
    eventualResponse.map(Xor.right)
      .recover {
        case NonFatal(t) =>
          errorLogger.foreach(log => log(t))
          Xor.left(PlayHttpError(t.getMessage))
      }
  }

  private def headerCarrier(request: WSRequestHolder): HeaderCarrier = {
    HeaderCarrier.fromHeadersAndSession(new Headers {
      override protected val data: Seq[(String, Seq[String])] = request.headers.toSeq
    })
  }
}

object HttpStreamingBody {

  case class Result(response: String, status: Int)

}

class HttpStreamingBody(url: String,
                        contentType: String = "application/octet-stream",
                        method: String = "POST",
                        auditer: (Boolean, Int, String) => (Request[_]) => Future[AuditResult],
                        request: Request[_],
                        contentLength: Option[Long] = None,
                        debug: Boolean = true) extends Iteratee[Array[Byte], HttpStreamingBody.Result] {

  require(Seq("POST", "PUT").contains(method.toUpperCase()), "Only POST/PUT supported")

  var mayBeConnection = Option.empty[HttpURLConnection]
  var mayBeResult = Option.empty[HttpStreamingBody.Result]
  var mayBeOutput = Option.empty[OutputStream]

  Try(new URL(url).openConnection().asInstanceOf[HttpURLConnection]) match {
    case Failure(NonFatal(e)) =>
      logError(e)
      mayBeResult = Some(HttpStreamingBody.Result(e.getMessage, 400))
    case Success(con) => mayBeConnection = Some(con)
  }

  mayBeConnection foreach { connection =>
    Try {
      connection.setRequestMethod(method)
      connection.setRequestProperty("Content-Type", contentType)
      contentLength foreach connection.setFixedLengthStreamingMode
      connection.setDoInput(true)
      connection.setDoOutput(true)
      connection.connect()
      mayBeOutput = Some(connection.getOutputStream)
    } match {
      case Failure(NonFatal(e)) =>
        logError(e)
        mayBeResult = Some(HttpStreamingBody.Result(e.getMessage, 400))
      case _ => ()
    }
  }


  def fold[B](folder: (Step[Array[Byte], HttpStreamingBody.Result]) => Future[B])(implicit ec: ExecutionContext): Future[B] = {
    val successful = true
    if (mayBeResult.isDefined) {
      folder(Step.Done(mayBeResult.get, Input.Empty))
    } else {
      folder(Step.Cont {
        case Input.EOF =>
          mayBeResult = mayBeConnection.map { con =>
            HttpStreamingBody.Result(con.getResponseMessage, con.getResponseCode)
          }
          mayBeConnection foreach (_.disconnect())
          Done(mayBeResult.get, Input.EOF)

        case Input.Empty => this
        case Input.El(data) =>

          Try(mayBeOutput foreach (_.write(data))) match {
            case Failure(NonFatal(e)) =>
              logError(e)
              mayBeResult = Some(HttpStreamingBody.Result(e.getMessage, 400))
            case _ => ()
          }
          this  //@todo this suggests we carry on consuming. is this what we want?
      })
    }
  }

  def logError(e: Throwable): Unit = {
    if (debug) e.printStackTrace()
  }
}
