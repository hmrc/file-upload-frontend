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
import play.api.libs.json.{Format, Json}
import play.api.libs.streams.Accumulator
import play.api.mvc.Results._
import play.api.mvc.{EssentialAction, RequestHeader, Result}
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.transfer.TransferService.{EnvelopeStatusNotFoundError, _}
import uk.gov.hmrc.fileupload.utils.StreamsConverter

import scala.concurrent.{ExecutionContext, Future}

object EnvelopeChecker {

  type WithValidEnvelope = EnvelopeId => EssentialAction => EssentialAction
  type WithConstraintsFromEnvelope = EnvelopeId => Future[ConstraintsFormEnvelope]

  import uk.gov.hmrc.fileupload.utils.StreamImplicits.materializer

  def withValidEnvelope(check: (EnvelopeId) => Future[EnvelopeStatusResult])
                       (envelopeId: EnvelopeId)
                       (action: EssentialAction)
                       (implicit ec: ExecutionContext) =
    EssentialAction { implicit rh =>
      Accumulator.flatten {
        check(envelopeId).map {
          case Xor.Right("OPEN") =>
            action(rh)
          case Xor.Right(otherStatus) =>
            logAndReturn(LOCKED, s"Unable to upload to envelope: $envelopeId with status: $otherStatus")
          case Xor.Left(EnvelopeStatusNotFoundError(_)) =>
            logAndReturn(NOT_FOUND, s"Unable to upload to nonexistent envelope: $envelopeId")
          case Xor.Left(error) =>
            logAndReturn(INTERNAL_SERVER_ERROR, error.toString)
        }
      }
    }

  def loadConstraintsFromEnvelope(check: (EnvelopeId) => Future[EnvelopeConstraintsResult])
                                 (envelopeId: EnvelopeId)(implicit ec: ExecutionContext) = {
    check(envelopeId).map {
      case Xor.Right(constraints) => constraints
      case Xor.Left(error) => throw new IllegalStateException(s"Constraints for $envelopeId need specified, ${error.toString}")
    }
  }

  private def logAndReturn(statusCode: Int, problem: String)(implicit rh: RequestHeader) = {
    Logger.warn(s"Request: $rh failed because: $problem")
    val iteratee = Done[Array[Byte], Result](new Status(statusCode).apply(Json.obj("message" -> problem)))
    StreamsConverter.iterateeToAccumulator(iteratee)
  }

  case class ConstraintsFormEnvelope(maxNumFiles: Int,
                         maxSize: String,
                         maxSizePerItem: String)

  object ConstraintsFormEnvelope {
    implicit val constraintsFormat: Format[ConstraintsFormEnvelope] = Json.format[ConstraintsFormEnvelope]
  }

  def sizeToByte(size: String): Long = {
    val sizeRegex = "([1-9][0-9]{0,3})([KB,MB]{2})".r
    size.toUpperCase match {
      case sizeRegex(num, unit) =>
        unit match {
          case "KB" => num.toInt * 1024
          case "MB" => num.toInt * 1024 * 1024
          case _ => -1
        }
      case _ => -1
    }
  }

}
