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

package uk.gov.hmrc.fileupload.utils

import play.api._
import play.api.http.HeaderNames._
import play.api.i18n.Messages
import play.api.libs.json.JsObject
import play.api.mvc.Results._
import play.api.mvc.{Result, _}
import uk.gov.hmrc.play.frontend.exceptions.ApplicationException

import scala.concurrent.Future

trait ShowErrorAsJson extends GlobalSettings {

  private implicit def rhToRequest(rh: RequestHeader) : Request[_] = Request(rh, "")

  def standardErrorTemplate(message: String)(implicit request: Request[_]): JsObject

  def badRequestTemplate(implicit request: Request[_]): JsObject = standardErrorTemplate(
    Messages("global.error.badRequest400.message"))

  def notFoundTemplate(implicit request: Request[_]): JsObject = standardErrorTemplate(
    Messages("global.error.pageNotFound404.message"))

  def internalServerErrorTemplate(implicit request: Request[_]): JsObject = standardErrorTemplate(
    Messages("global.error.InternalServerError500.message"))

  final override def onBadRequest(rh: RequestHeader, error: String) =
    Future.successful(BadRequest(badRequestTemplate(rh)))

  final override def onError(request: RequestHeader, ex: Throwable): Future[Result] =
    Future.successful(resolveError(request, ex))

  final override def onHandlerNotFound(rh: RequestHeader) =
    Future.successful(NotFound(notFoundTemplate(rh)))

  def resolveError(rh: RequestHeader, ex: Throwable) = ex.getCause match {
    case ApplicationException(domain, result, _) => result
    case _ => InternalServerError(internalServerErrorTemplate(rh)).withHeaders(CACHE_CONTROL -> "no-cache")
  }

}
