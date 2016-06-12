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

package uk.gov.hmrc.fileupload.controllers

import play.api.Logger
import play.api.libs.Files.TemporaryFile
import uk.gov.hmrc.play.frontend.controller.FrontendController
import play.api.mvc._
import uk.gov.hmrc.play.http.BadRequestException

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


object FileUploadController extends FileUploadController

trait FileUploadController extends FrontendController {

  def upload() = Action.async(parse.multipartFormData) { implicit request =>
    doUpload(request)
  }

  def doUpload(request: Request[MultipartFormData[TemporaryFile]]) = {
    Try(UploadParameters(request.body.dataParts)) match {
      case Failure(e) =>
        Logger.info(s"Exception: $e was thrown")
        Future.successful(BadRequest)
      case Success(params:UploadParameters) =>
        Future.successful(MovedPermanently(params.successRedirect))
    }
  }
}

sealed case class UploadParameters(successRedirect:String, failureRedirect:String, envelopeId:String, fileId:String)

object UploadParameters {
  def apply(dataParts:Map[String, Seq[String]]): UploadParameters = {
    implicit val filteredParams = dataParts.mapValues(toFirstValue).filter(removeEntriesWithNoValue)

    UploadParameters(requiredValue("successRedirect"),
                     requiredValue("failureRedirect"),
                     requiredValue("envelopeId"),
                     requiredValue("fileId"))
  }

  private def requiredValue(key:String)(implicit map:Map[String, Option[String]]): String = {
    map.getOrElse(key, throw new BadRequestException(s"$key is missing")).get
  }

  private def toFirstValue(vals: Seq[String]): Option[String] = vals.headOption match {
    case some @ Some(v) if v.trim.length > 0 => some
    case _ => None
  }

  private def removeEntriesWithNoValue(t: (String, Option[String])): Boolean = t._2.isDefined
}