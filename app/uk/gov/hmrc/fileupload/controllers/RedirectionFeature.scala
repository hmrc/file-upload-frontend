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

package uk.gov.hmrc.fileupload.controllers


import java.net.{MalformedURLException, URL}

import akka.util.ByteString
import com.typesafe.config.Config
import play.api.Logger
import play.api.http.{HttpEntity, HttpErrorHandler}
import play.api.http.Status.{BAD_REQUEST, MOVED_PERMANENTLY}
import play.api.libs.iteratee.Done
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc.Results.Status
import play.api.mvc._
import uk.gov.hmrc.fileupload.utils.StreamsConverter

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}


class RedirectionFeature(allowedHosts: Seq[String], errorHandler: HttpErrorHandler) {
  import RedirectionFeature._

  def this(config: Config, errorHandler: HttpErrorHandler) =
    this(config.getString("controllers.redirection.allowedHosts")
      .split(",").toSeq
      .map(_.trim), errorHandler)

  private val isLocalHostAllowed = allowedHosts.contains(LOCAL_HOST)

  def redirect(successUrlO: Option[String], failureUrlO: Option[String])
              (task: => EssentialAction)
              (implicit ec: ExecutionContext): EssentialAction = EssentialAction { implicit rh =>

    val validation: Try[RedirectUrlsO] = {
      val sO = successUrlO.map(validateAndSanitize)
      val fO = failureUrlO.map(validateAndSanitize)

      optTry2TryOpt(sO, fO)
    }

    validation match {
      case Failure(ex) => logUrlProblemAndReturn(BAD_REQUEST, ex)
      case Success(redirectParams) =>
        task(rh).recoverWith {
          case e: Throwable => errorHandler.onServerError(rh, e)
        }.map(
          redirectTheResult(redirectParams, _)
        )
    }
  }

  private def redirectTheResult(redirectParams: RedirectUrlsO, result: Result) = {
    val newUrlO = {
      import result.header.status

      if (status > 199 && status < 300)
        redirectParams.succ
      else // only our logical errors lands here
        redirectParams.fail
          .map(addErrorDataToUrl(status, extractErrorMsg(result)))
    }

    newUrlO.map(redirectToUrl)
      .getOrElse(result)
  }

  def validateAndSanitize(url: String): Try[ValidatedUrl] = {
    Try{
      val suspect = new URL(url)
      if(!(suspect.getProtocol == "https" || (isLocalHostAllowed && suspect.getHost == LOCAL_HOST)))
        throw new MalformedURLException("Https is required for the redirection.")
      if(!allowedHosts.exists(base => suspect.getHost.endsWith(base)))
        throw new MalformedURLException("Given redirection domain is not allowed.")
    }.map( _ => ValidatedUrl(
      url.takeWhile( c => !(c=='?' || c=='#'))
    ))
  }
}

object RedirectionFeature {
  val LOCAL_HOST = "localhost"
  val MAX_URL_LENGHT = 2000

  case class ValidatedUrl(url: String)
  case class RedirectUrlsO(succ: Option[ValidatedUrl], fail: Option[ValidatedUrl])

  def logUrlProblemAndReturn(statusCode: Int, problem: Throwable)
                            (implicit rh: RequestHeader): Accumulator[ByteString, Result] = {
    Logger.warn(s"Request: $rh failed because: ${problem.toString}")
    val iteratee = Done[Array[Byte], Result](new Status(statusCode).apply(Json.obj("message" -> "URL is invalid")))
    StreamsConverter.iterateeToAccumulator(iteratee)
  }
  // with cats it should be much better:
  private def optTry2TryOpt(sO: Option[Try[ValidatedUrl]], fO: Option[Try[ValidatedUrl]]) = {
    val failureCheck = Seq(sO, fO).flatten.filter(_.isFailure)

    if (failureCheck.nonEmpty)
      Failure(failureCheck.head.failed.get)
    else
      Success(RedirectUrlsO(sO.map(_.get), fO.map(_.get)))
  }

  def redirectToUrl(url: ValidatedUrl): Result =
    Results.Redirect(url.url, MOVED_PERMANENTLY)

  def addErrorDataToUrl(status: Int, msg: String): ValidatedUrl => ValidatedUrl = baseUrl => {
    val first = baseUrl.url + s"?errorCode=$status&reason="
    val restLength = MAX_URL_LENGHT - first.length
    ValidatedUrl(first + msg.take(restLength))
  }

  def extractErrorMsg(result: Result): String = {
    result.body.asInstanceOf[HttpEntity.Strict].data.decodeString("utf-8") // how to do it cleanly?
  }
}
