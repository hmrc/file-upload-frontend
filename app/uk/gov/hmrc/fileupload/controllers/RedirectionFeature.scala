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

package uk.gov.hmrc.fileupload.controllers

import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.ByteString
import cats.implicits._
import com.typesafe.config.Config
import play.api.Logger
import play.api.http.HttpErrorHandler
import play.api.http.Status.{BAD_REQUEST, MOVED_PERMANENTLY}
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc.Results.Status
import play.api.mvc.{EssentialAction, RequestHeader, Result, Results}

import java.net.{MalformedURLException, URL}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


class RedirectionFeature(allowedHosts: Seq[String], errorHandler: HttpErrorHandler):
  import RedirectionFeature._

  def this(config: Config, errorHandler: HttpErrorHandler) =
    this(
      config
        .getString("controllers.redirection.allowedHosts")
        .split(",")
        .toSeq
        .map(_.trim),
      errorHandler
    )

  private val isLocalHostAllowed: Boolean =
    allowedHosts.contains(LOCAL_HOST)

  def redirect(
    successUrlO: Option[String],
    failureUrlO: Option[String]
  )(task: => EssentialAction
  )(using
    Materializer,
    ExecutionContext
  ): EssentialAction =
    EssentialAction { implicit rh =>
      val validation: Try[RedirectUrlsO] =
        for
          optS <- successUrlO.map(validateAndSanitize).sequence
          optF <- failureUrlO.map(validateAndSanitize).sequence
        yield RedirectUrlsO(optS, optF)

      validation match {
        case Failure(ex) =>
          logUrlProblemAndReturn(BAD_REQUEST, ex)
        case Success(redirectParams) =>
          task(rh)
            .recoverWith:
              case e: Throwable => errorHandler.onServerError(rh, e)
            .mapFuture(redirectTheResult(redirectParams, _))
      }
    }

  private def redirectTheResult(
    redirectParams: RedirectUrlsO,
    result: Result
  )(using Materializer, ExecutionContext): Future[Result] =
    import result.header.status
    val newUrlO =
      if status > 199 && status < 300 then
        Future.successful(redirectParams.succ)
      else // only our logical errors lands here
        extractErrorMsg(result)
          .map: errorMsg =>
            redirectParams.fail
              .map(addErrorDataToUrl(status, errorMsg))
    newUrlO.map(_.fold(result)(redirectToUrl))

  def validateAndSanitize(url: String): Try[ValidatedUrl] =
    Try {
      val suspect = URL(url)

      if (suspect.getProtocol != "https" && (!isLocalHostAllowed || suspect.getHost != LOCAL_HOST))
        throw MalformedURLException("Https is required for the redirection.")

      if (!allowedHosts.exists:
        case base if base.startsWith("*") => suspect.getHost.endsWith(base.drop(1))
        case base                         => suspect.getHost.equalsIgnoreCase(base)
      )
        throw MalformedURLException("Given redirection domain is not allowed.")

    }.map(_ => ValidatedUrl(url.takeWhile(c => (c != '?' && c != '#'))))

end RedirectionFeature

object RedirectionFeature:
  private val logger = Logger(getClass)

  val LOCAL_HOST = "localhost"
  val MAX_URL_LENGTH = 2000

  case class ValidatedUrl(url: String)
  case class RedirectUrlsO(succ: Option[ValidatedUrl], fail: Option[ValidatedUrl])

  def logUrlProblemAndReturn(
    statusCode: Int,
    problem   : Throwable
  )(using rh: RequestHeader): Accumulator[ByteString, Result] =
    logger.warn(s"Request: $rh failed because: ${problem.toString}")
    Accumulator.done(Status(statusCode).apply(Json.obj("message" -> "URL is invalid")))

  def redirectToUrl(url: ValidatedUrl): Result =
    Results.Redirect(url.url, MOVED_PERMANENTLY)

  def addErrorDataToUrl(status: Int, msg: String): ValidatedUrl => ValidatedUrl =
    baseUrl =>
      val first = baseUrl.url + s"?errorCode=$status&reason="
      val restLength = MAX_URL_LENGTH - first.length
      ValidatedUrl(first + msg.take(restLength))

  def extractErrorMsg(result: Result)(using Materializer, ExecutionContext): Future[String] =
    result.body.consumeData.map(_.decodeString("utf-8"))

end RedirectionFeature
