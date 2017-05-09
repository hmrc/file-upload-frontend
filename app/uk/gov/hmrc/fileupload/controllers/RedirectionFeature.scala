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

import com.typesafe.config.Config
import play.api.http.HttpEntity
import play.api.http.Status.{BAD_REQUEST, MOVED_PERMANENTLY}
import play.api.mvc._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}


class RedirectionFeature(allowedHosts: Seq[String]) {

  def this(config: Config) =
    this(config.getString("controllers.redirection.allowedHosts")
      .split(",").toSeq
      .map(_.trim))

  val LOCAL_HOST = "localhost"
  private val isLocalHostAllowed = allowedHosts.contains(LOCAL_HOST)

  import EnvelopeChecker.logAndReturn

  case class ValidatedUrl(url: String)
  case class RedirectUrlsO(succ: Option[ValidatedUrl], fail: Option[ValidatedUrl])

  def redirect(successUrlO: Option[String], failureUrlO: Option[String])
              (task: => EssentialAction)
              (implicit ec: ExecutionContext): EssentialAction = EssentialAction { implicit rh =>

    val validation: Try[RedirectUrlsO] = {
      val sO = successUrlO.map(validateAndSanitize)
      val fO = failureUrlO.map(validateAndSanitize)

      optTry2TryOpt(sO, fO)
    }

    validation match {
      case Failure(ex) => logAndReturn(BAD_REQUEST, ex.toString)
      case Success(redirectParams) =>
        task(rh).map{ wrappedResult =>
          val newUrlO = {
            import wrappedResult.header.status

            if( status > 199 && status < 300)
              redirectParams.succ
            else // only our logical errors lands here
              redirectParams.fail
                .map(addErrorDataToUrl(status, extractErrorMsg(wrappedResult)))
          }

          newUrlO.map(redirectToUrl)
            .getOrElse(wrappedResult)
        }
    }
  }

  def redirectToUrl(url: ValidatedUrl): Result = Results.Redirect(url.url, MOVED_PERMANENTLY)

  def addErrorDataToUrl(status: Int, msg: String): ValidatedUrl => ValidatedUrl =
    baseUrl => ValidatedUrl(baseUrl.url + s"?errorCode:$status&reason=$msg")

  def extractErrorMsg(result: Result): String = {
    result.body.asInstanceOf[HttpEntity.Strict].data.decodeString("utf-8") // how to do it cleanly?
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
  // with cats it should be much better:
  private def optTry2TryOpt(sO: Option[Try[ValidatedUrl]], fO: Option[Try[ValidatedUrl]]) = {
    val failureCheck = Seq(sO, fO).flatten.filter(_.isFailure)

    if (failureCheck.nonEmpty)
      Failure(failureCheck.head.failed.get)
    else
      Success(RedirectUrlsO(sO.map(_.get), fO.map(_.get)))
  }
}
