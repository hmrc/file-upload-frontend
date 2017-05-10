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

import akka.util.ByteString
import cats.data.Xor
import play.api.{Configuration, Logger}
import play.api.http.Status._
import play.api.libs.iteratee.Done
import play.api.libs.json.{JsValue, Json}
import play.api.libs.streams.Accumulator
import play.api.mvc.Results._
import play.api.mvc.{EssentialAction, MultipartFormData, RequestHeader, Result}
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.quarantine.Constraints
import uk.gov.hmrc.fileupload.s3.InMemoryMultipartFileHandler.FileCachedInMemory
import uk.gov.hmrc.fileupload.transfer.TransferService._
import uk.gov.hmrc.fileupload.utils.StreamsConverter

import scala.concurrent.{ExecutionContext, Future}

object EnvelopeChecker {

  type FileSize = Long
  type ContentType = String
  type WithValidEnvelope =
    EnvelopeId => (FileSize => List[ContentType] => EssentialAction) => EssentialAction

  import uk.gov.hmrc.fileupload.utils.StreamImplicits.materializer

  val defaultFileSize: FileSize = (10 * 1024 * 1024).toLong //bytes
  val defaultContentTypes: List[ContentType] = List("application/pdf","image/jpeg","application/xml", "text/xml")

  def withValidEnvelope(checkEnvelopeDetails: (EnvelopeId) => Future[EnvelopeDetailResult])
                       (envelopeId: EnvelopeId)
                       (action: FileSize => List[ContentType] => EssentialAction)
                       (implicit ec: ExecutionContext) =
    EssentialAction { implicit rh =>
      Accumulator.flatten {
        checkEnvelopeDetails(envelopeId).map {
          case Xor.Right(envelope) =>
            val status = (envelope \ "status").as[String]
            status match {
              case "OPEN" => action(getMaxFileSizeFromEnvelope(envelope))(getContentTypeFromEnvelope(envelope))(rh)
              case "CLOSED" | "SEALED" => logAndReturn(LOCKED, s"Unable to upload to envelope: $envelopeId with status: $status")
              case _ => logAndReturn(BAD_REQUEST, s"Unable to upload to envelope: $envelopeId with status: $status")
            }
          case Xor.Left(EnvelopeDetailNotFoundError(_)) =>
            logAndReturn(NOT_FOUND, s"Unable to upload to nonexistent envelope: $envelopeId")
          case Xor.Left(error) =>
            logAndReturn(INTERNAL_SERVER_ERROR, error.toString)
        }
      }
    }

  def getMaxFileSizeFromEnvelope(envelope: JsValue): FileSize = {
    val definedConstraints = (envelope \ "constraints").asOpt[Constraints]
       definedConstraints match {
         case Some(constraints) => constraints.maxSizePerItem match {
           case Some(maxSizePerItem) => maxSizePerItem
           case None => defaultFileSize
         }
         case None => defaultFileSize
      }
  }

  def getContentTypeFromEnvelope(envelope: JsValue): List[ContentType] = {
    val definedConstraints = (envelope \ "constraints").asOpt[Constraints]
    definedConstraints.flatMap(_.contentTypes).getOrElse(defaultContentTypes)
  }

  def getFormContentType(getFormContentType: MultipartFormData[FileCachedInMemory]): ContentType = {
    getFormContentType.files
      .flatMap(_.contentType)
      .headOption.getOrElse("")
  }

  def containsContentType(formContentType: ContentType, envelopeContentType: List[ContentType]): Boolean = {
    if(formContentType.equals("text/xml")){
      true
    } else {
      envelopeContentType.contains(formContentType)
    }
  }

  def logAndReturn(statusCode: Int, problem: String)
                          (implicit rh: RequestHeader): Accumulator[ByteString, Result] = {
    Logger.warn(s"Request: $rh failed because: $problem")
    val iteratee = Done[Array[Byte], Result](new Status(statusCode).apply(Json.obj("message" -> problem)))
    StreamsConverter.iterateeToAccumulator(iteratee)
  }
}
