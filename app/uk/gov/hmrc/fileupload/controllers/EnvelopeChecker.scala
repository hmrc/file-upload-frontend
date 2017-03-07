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

import cats.data.Xor
import play.api.Logger
import play.api.http.Status._
import play.api.libs.iteratee.Done
import play.api.libs.json.{JsValue, Json}
import play.api.libs.streams.Accumulator
import play.api.mvc.Results._
import play.api.mvc.{EssentialAction, RequestHeader, Result}
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.quarantine.Constraints
import uk.gov.hmrc.fileupload.transfer.TransferService._
import uk.gov.hmrc.fileupload.utils.StreamsConverter

import scala.concurrent.{ExecutionContext, Future}

object EnvelopeChecker {

  type FileSize = Int
  type WithValidEnvelope = EnvelopeId => (FileSize => EssentialAction) => EssentialAction

  import uk.gov.hmrc.fileupload.utils.StreamImplicits.materializer

  val defaultFileSize = 10 * 1024 * 1024

  def withValidEnvelope(checkEnvelopeDetails: (EnvelopeId) => Future[EnvelopeDetailResult])
                       (envelopeId: EnvelopeId)
                       (action: FileSize => EssentialAction)
                       (implicit ec: ExecutionContext) =
    EssentialAction { implicit rh =>
      Accumulator.flatten {
        checkEnvelopeDetails(envelopeId).map {
          case Xor.Right(envelope) =>
            val status = (envelope \ "status").as[String]
            if (status == "OPEN") {
              action(setMaxFileSize(envelope))(rh)
            } else if (status == "CLOSED") {
              logAndReturn(LOCKED, s"Unable to upload to envelope: $envelopeId with status: $status")
            } else if(status == "SEALED") {
              logAndReturn(LOCKED, s"Unable to upload to envelope: $envelopeId with status: $status")
            } else {
              //If uploading to deleted Envelope
              logAndReturn(BAD_REQUEST, s"Unable to upload to envelope: $envelopeId with status: $status")
            }
          case Xor.Left(EnvelopeDetailNotFoundError(_)) =>
            logAndReturn(NOT_FOUND, s"Unable to upload to nonexistent envelope: $envelopeId")
          case Xor.Left(error) =>
            logAndReturn(INTERNAL_SERVER_ERROR, error.toString)
        }
      }
    }

  def setMaxFileSize(envelope: JsValue): FileSize = {
    val definedConstraints = (envelope \ "constraints").asOpt[Constraints]
       definedConstraints match {
           //TODO Possibly in the future this may change to take into account the envelope size e.g. if envelope size is 5MB and the upload limit is 10MB
         case Some(constraints) => constraints.maxSizePerItem match {
           case Some(maxSizePerItem) =>
             val fileSize = maxSizePerItem.replaceAll("[^\\d.]", "").toInt
             val fileSizeType = maxSizePerItem.toUpperCase.replaceAll("[^KMB]", "")
             fileSizeType match {
               case "KB" => fileSize * 1024
               case "MB" =>
                 if(fileSize <= 10){
                   fileSize * 1024 * 1024
                 } else {
                   //If maxSizePerItem is accidentally more than 10MB e.g. 100MB
                   defaultFileSize
                 }
                 //If maxSizePerItem is GB or TB
               case _ => defaultFileSize
             }
             //If constraint's maxSizePerItem exist or not
           case None => defaultFileSize
         }
         //If constraints not specified
         case None => defaultFileSize
      }
  }

  private def logAndReturn(statusCode: Int, problem: String)(implicit rh: RequestHeader) = {
    Logger.warn(s"Request: $rh failed because: $problem")
    val iteratee = Done[Array[Byte], Result](new Status(statusCode).apply(Json.obj("message" -> problem)))
    StreamsConverter.iterateeToAccumulator(iteratee)
  }
}
