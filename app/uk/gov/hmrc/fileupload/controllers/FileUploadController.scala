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

import play.api.libs.iteratee.{Concurrent, Enumerator, Iteratee}
import play.api.mvc.BodyParsers.parse.{Multipart, _}
import play.api.mvc._
import play.modules.reactivemongo.MongoDbConnection
import uk.gov.hmrc.fileupload.connectors.{FileUploadConnector, _}
import uk.gov.hmrc.fileupload.services.UploadService
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.util.{Failure, Try}

object FileUploadController extends FileUploadController with MongoQuarantineStoreConnector with FileUploadConnector
  with ServicesConfig with MongoDbConnection with ClamAvScannerConnector

trait FileUploadController extends FrontendController with UploadService with QuarantineStoreConnector with AvScannerConnector {

  import UploadParameters._
  import uk.gov.hmrc.fileupload.connectors._

  import scala.concurrent.ExecutionContext.Implicits.global

  val invalidEnvelope = "invalidParam=envelopeId"
  val persistenceFailure = "persistenceFailure=true"

  def sendRedirect(destination: String) = Future.successful(SeeOther(destination))

  def postValidationRouting(fail: String, success: String): PartialFunction[(Try[String], Try[String]), String] = {
    case (Failure(_), _) => s"$fail?$invalidEnvelope"
    case (_, (Failure(_))) => s"$fail?$persistenceFailure"
    case _ => success
  }

  def enumeratorBodyParser = multipartFormData(Multipart.handleFilePart {
    case Multipart.FileInfo(partName, filename, contentType) =>
      val (enum, channel) = Concurrent.broadcast[Array[Byte]]

      Iteratee.foreach[Array[Byte]] { channel.push }.map { _ =>
        channel.eofAndEnd()
        enum
      }
  })

  def upload() = Action.async(enumeratorBodyParser) { implicit request =>
    doUpload(request)
  }

  def doUpload(request: Request[MultipartFormData[Enumerator[Array[Byte]]]])(implicit hc: HeaderCarrier) = {
    UploadParameters(request.body.dataParts, request.body.files) match {
      case params@UploadParameters(Some(successRedirect), Some(failureRedirect), Some(envelopeId), Seq(filePart)) =>
        for {
          redirectTo <- validateAndPersist(filePart) map postValidationRouting(failureRedirect, successRedirect)
          eventualUrl <- sendRedirect(redirectTo)
        } yield eventualUrl
      case params@UploadParameters(_, Some(failureRedirect), _, _) =>
        sendRedirect(failureRedirect + buildInvalidQueryString(params))
      case params@UploadParameters(_, None, _, _) =>
        request.headers.get("Referer") match {
          case Some(referer) => sendRedirect(referer + buildInvalidQueryString(params))
          case None => Future.successful(BadRequest)
        }
    }
  }

  sealed case class UploadParameters(successRedirect: Option[String],
                                     failureRedirect: Option[String],
                                     envelopeId: Option[String],
                                     files: Seq[FileData])

  object UploadParameters {
    def apply(dataParts: Map[String, Seq[String]], fileParts: Seq[MultipartFormData.FilePart[Enumerator[Array[Byte]]]]): UploadParameters = {
      implicit val filteredParams = dataParts.mapValues(toFirstValue).filter(removeEntriesWithNoValue)

      val files = fileParts.map { f =>
        FileData(f.ref, f.filename, f.contentType.getOrElse(""), getOptionValue("envelopeId").getOrElse(""), f.key)
      }

      UploadParameters(getOptionValue("successRedirect"),
        getOptionValue("failureRedirect"),
        getOptionValue("envelopeId"),
        files)
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
        "envelopeId" -> params.envelopeId,
        "file" -> fileMatch)
    }

    private def getOptionValue(key: String)(implicit map: Map[String, Option[String]]): Option[String] = {
      map.getOrElse(key, None)
    }

    private def toFirstValue(vals: Seq[String]): Option[String] = vals.headOption match {
      case some@Some(v) if v.trim.length > 0 => some
      case _ => None
    }

    private def removeEntriesWithNoValue(t: (String, Option[String])): Boolean = t._2.isDefined
  }
}
