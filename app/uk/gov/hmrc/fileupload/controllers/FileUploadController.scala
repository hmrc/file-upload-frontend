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

import play.api.libs.Files.TemporaryFile
import play.api.mvc._
import uk.gov.hmrc.fileupload.controllers.UploadParameters.buildInvalidQueryString
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future
import scala.util.parsing.json.JSONObject


object FileUploadController extends FileUploadController

trait FileUploadController extends FrontendController {

  def fileUploadConnector = ???

  def upload() = Action.async(parse.multipartFormData) { implicit request =>
    doUpload(request)
  }

  def doUpload(request: Request[MultipartFormData[TemporaryFile]]) = {
    UploadParameters(request.body.dataParts) match {
      case UploadParameters(Some(successRedirect), Some(_), Some(envelopeId), Some(fileId)) =>

        val envelope = fileUploadConnector.retrieveEnvelope(envelopeId)

        envelope match {
          case ??? => ???
        }

        Future.successful(SeeOther(successRedirect))
      case params @ UploadParameters(_, Some(failureRedirect), _, _) =>
        Future.successful(SeeOther(failureRedirect + buildInvalidQueryString(params)))
      case params @ UploadParameters(_, None, _, _) =>
        request.headers.get("Referer") match {
          case Some(referer) => Future.successful(SeeOther(referer + buildInvalidQueryString(params)))
          case None => Future.successful(BadRequest)
        }
    }
  }
}

sealed case class UploadParameters(successRedirect:Option[String], failureRedirect:Option[String], envelopeId:Option[String], fileId:Option[String])

object UploadParameters {
  def apply(dataParts:Map[String, Seq[String]]): UploadParameters = {
    implicit val filteredParams = dataParts.mapValues(toFirstValue).filter(removeEntriesWithNoValue)

    UploadParameters(getOptionValue("successRedirect"),
                     getOptionValue("failureRedirect"),
                     getOptionValue("envelopeId"),
                     getOptionValue("fileId"))
  }

  def buildInvalidQueryString(uploadParameters: UploadParameters): String = {
    "?" + asMap(uploadParameters).filter(_._2.isEmpty).map { entry => s"invalidParam=${entry._1}" }.mkString("&")
  }

  private def asMap(params: UploadParameters) = {
    Map("successRedirect" -> params.successRedirect, "failureRedirect" -> params.failureRedirect, "envelopeId" -> params.envelopeId, "fileId" -> params.fileId)
  }

  private def getOptionValue(key:String)(implicit map:Map[String, Option[String]]): Option[String] = {
    map.getOrElse(key, None)
  }

  private def toFirstValue(vals: Seq[String]): Option[String] = vals.headOption match {
    case some @ Some(v) if v.trim.length > 0 => some
    case _ => None
  }

  private def removeEntriesWithNoValue(t: (String, Option[String])): Boolean = t._2.isDefined
}