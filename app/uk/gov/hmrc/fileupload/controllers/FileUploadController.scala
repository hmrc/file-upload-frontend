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
    Try(UploadParameters(request.body.dataParts.map(findAndMapFirstParamValue))) match {
      case Failure(e) =>
        Logger.info(s"Exception: $e was thrown")
        Future.successful(BadRequest)
      case Success(params:UploadParameters) =>
        Future.successful(MovedPermanently(params.successRedirect))
    }
  }

  private def findAndMapFirstParamValue(kv: (String, Seq[String])): (String, String) = { kv match {
      case (k, Seq(v:String, _*)) if !Option(v).getOrElse("").isEmpty => (k, v)
      case _ => (null,null)
    }
  }
}

sealed case class UploadParameters(successRedirect:String, failureRedirect:String, envelopeId:String, fileId:String)

object UploadParameters {
  def apply(params:Map[String, String]): UploadParameters = {
    UploadParameters(params.getOrElse("successRedirect", throw new BadRequestException("successRedirect is missing")),
                     params.getOrElse("failureRedirect", throw new BadRequestException("failureRedirect is missing")),
                     params.getOrElse("envelopeId", throw new BadRequestException("envelopeId is missing")),
                     params.getOrElse("fileId", throw new BadRequestException("fileId is missing")))
  }
}