package uk.gov.hmrc.fileupload.utils

import play.api._
import play.api.http.HeaderNames._
import play.api.i18n.Messages
import play.api.libs.json.JsObject
import play.api.mvc.Results._
import play.api.mvc.{Result, _}
import uk.gov.hmrc.play.frontend.exceptions.ApplicationException

import scala.concurrent.Future

trait showErrorAsJson extends GlobalSettings {

  private implicit def rhToRequest(rh: RequestHeader) : Request[_] = Request(rh, "")

  def standardErrorTemplate(pageTitle: String, heading: String, message: String)(implicit request: Request[_]): JsObject

  def badRequestTemplate(implicit request: Request[_]): JsObject = standardErrorTemplate(
    Messages("global.error.badRequest400.title"),
    Messages("global.error.badRequest400.heading"),
    Messages("global.error.badRequest400.message"))

  def notFoundTemplate(implicit request: Request[_]): JsObject = standardErrorTemplate(
    Messages("global.error.pageNotFound404.title"),
    Messages("global.error.pageNotFound404.heading"),
    Messages("global.error.pageNotFound404.message"))

  def internalServerErrorTemplate(implicit request: Request[_]): JsObject = standardErrorTemplate(
    Messages("global.error.InternalServerError500.title"),
    Messages("global.error.InternalServerError500.heading"),
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
