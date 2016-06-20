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
import uk.gov.hmrc.fileupload.connectors.{FileUploadConnector, InvalidEnvelope, ValidEnvelope}
import uk.gov.hmrc.fileupload.controllers.UploadParameters.buildInvalidQueryString
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

object FileUploadController extends FileUploadController {
  override lazy val fileUploadConnector = FileUploadConnector
  }

trait FileUploadController extends FrontendController {

  lazy val fileUploadConnector:FileUploadConnector = ???

  def upload() = Action.async(parse.multipartFormData) { implicit request =>
    doUpload(request)
  }

  def doUpload(request: Request[MultipartFormData[TemporaryFile]]) = {
    UploadParameters(request.body.dataParts, request.body.files) match {
      case params @ UploadParameters(Some(successRedirect), Some(failureRedirect), Some(envelopeId), Some(fileId), Seq(filePart)) =>
        fileUploadConnector.retrieveEnvelope(envelopeId) match {
          case env:ValidEnvelope if env.fileIds.contains(fileId) => sendRedirect(successRedirect)
          case _:ValidEnvelope => sendRedirect(failureRedirect + "?invalidParam=fileId")
          case InvalidEnvelope => sendRedirect(failureRedirect + "?invalidParam=envelopeId")
        }
      case params @ UploadParameters(_, Some(failureRedirect), _, _, _) =>
        sendRedirect(failureRedirect + buildInvalidQueryString(params))
      case params @ UploadParameters(_, None, _, _, _) =>
        request.headers.get("Referer") match {
          case Some(referer) => sendRedirect(referer + buildInvalidQueryString(params))
          case None => Future.successful(BadRequest)
        }
    }
  }
  
  def sendRedirect(destination:String) = {
    Future.successful(SeeOther(destination))
  }
}

sealed case class UploadParameters(successRedirect:Option[String],
                                   failureRedirect:Option[String],
                                   envelopeId:Option[String],
                                   fileId:Option[String],
                                   files:Seq[MultipartFormData.FilePart[TemporaryFile]])

object UploadParameters {
  def apply(dataParts:Map[String, Seq[String]], fileParts:Seq[MultipartFormData.FilePart[TemporaryFile]]): UploadParameters = {
    implicit val filteredParams = dataParts.mapValues(toFirstValue).filter(removeEntriesWithNoValue)

    UploadParameters(getOptionValue("successRedirect"),
                     getOptionValue("failureRedirect"),
                     getOptionValue("envelopeId"),
                     getOptionValue("fileId"),
                     fileParts)
  }

  def buildInvalidQueryString(uploadParameters: UploadParameters): String = {
    asMap(uploadParameters).filter(_._2.isEmpty).map { entry => s"invalidParam=${entry._1}" }.mkString("?", "&", "")
  }

  private def asMap(params: UploadParameters) = {
    val fileMatch = params.files match {
      case Seq(singleFile) => Some("exists")
      case _ => None
    }

    Map("successRedirect" -> params.successRedirect,
        "failureRedirect" -> params.failureRedirect,
        "envelopeId"      -> params.envelopeId,
        "fileId"          -> params.fileId,
        "file"            -> fileMatch)
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