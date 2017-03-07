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

  def withValidEnvelope(check: (EnvelopeId) => Future[EnvelopeDetailResult])
                       (envelopeId: EnvelopeId)
                       (action: FileSize => EssentialAction)
                       (implicit ec: ExecutionContext) =
    EssentialAction { implicit rh =>
      Accumulator.flatten {
        check(envelopeId).map {
          case (Xor.Right(envelope)) =>
            val status = (envelope \ "status").as[String]
            if (status == "OPEN") {
              action(checkSizeLimit(envelope))(rh)
            } else {
              logAndReturn(LOCKED, s"Unable to upload to envelope: $envelopeId with status: $status")
            }
          case (Xor.Left(EnvelopeDetailNotFoundError(_))) =>
            logAndReturn(NOT_FOUND, s"Unable to upload to nonexistent envelope: $envelopeId")
          case (Xor.Left(error)) =>
            logAndReturn(INTERNAL_SERVER_ERROR, error.toString)
        }
      }
    }

  def checkSizeLimit(envelope: JsValue) = (
    envelope \ "constraints").as[Constraints].maxSizePerItem match {
    case Some(s) =>
      val fileSize = s.replaceAll("[^\\d.]", "").toInt + 1
      val fileSizeType = s.toUpperCase.replaceAll("[^KB,MB]{2}", "")
      fileSizeType match {
        case "KB" => fileSize * 1024
        case "MB" => fileSize * 1024 * 1024
      }
    case None => 11 * 1024 * 1024
  }

  private def logAndReturn(statusCode: Int, problem: String)(implicit rh: RequestHeader) = {
    Logger.warn(s"Request: $rh failed because: $problem")
    val iteratee = Done[Array[Byte], Result](new Status(statusCode).apply(Json.obj("message" -> problem)))
    StreamsConverter.iterateeToAccumulator(iteratee)
  }
}
