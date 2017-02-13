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

package uk.gov.hmrc.fileupload.utils

import play.api._
import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND}
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc._
import uk.gov.hmrc.play.http.{HttpException, Upstream4xxResponse, Upstream5xxResponse}

import scala.concurrent.Future

/**
  * Copy from microservice-bootstrap_2.11-4.4.0-sources.jar.uk.gov.hmrc.play.microservice.bootstrap.JsonErrorHandling.scala
  * The above class has been in production for sometime, so can be trusted.
  */

case class ErrorResponse(statusCode: Int, message: String, xStatusCode: Option[String] = None, requested: Option[String] = None)

trait ShowErrorAsJson extends GlobalSettings {

  implicit val erFormats = Json.format[ErrorResponse]

  override def onError(request: RequestHeader, ex: Throwable) = {
    Future.successful {
      val (code, message) = ex match {
        case e: HttpException => (e.responseCode, e.getMessage)

        case e: Upstream4xxResponse => (e.reportAs, e.getMessage)
        case e: Upstream5xxResponse => (e.reportAs, e.getMessage)

        case e: Throwable => (INTERNAL_SERVER_ERROR, e.getMessage)
      }

      new Status(code)(Json.toJson(ErrorResponse(code, message)))
    }
  }

  override def onHandlerNotFound(request: RequestHeader) = {
    Future.successful {
      val er = ErrorResponse(NOT_FOUND, "URI not found", requested = Some(request.path))
      NotFound(Json.toJson(er))
    }
  }

  override def onBadRequest(request: RequestHeader, error: String) = {
    Future.successful {
      val er = ErrorResponse(BAD_REQUEST, error)
      BadRequest(Json.toJson(er))
    }
  }

}
