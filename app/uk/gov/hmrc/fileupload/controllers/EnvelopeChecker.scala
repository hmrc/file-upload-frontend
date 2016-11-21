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

import cats.data.Xor
import play.api.Logger
import play.api.http.Status._
import play.api.libs.iteratee.{Done, Iteratee}
import play.api.libs.json.Json
import play.api.mvc.{EssentialAction, RequestHeader, Result}
import play.api.mvc.Results._
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.transfer.TransferService.{EnvelopeStatusNotFoundError, _}

import scala.concurrent.{ExecutionContext, Future}

object EnvelopeChecker {

  def withExistingEnvelope(envelopeId: EnvelopeId, check: (EnvelopeId) => Future[EnvelopeStatusResult])
                          (block: EssentialAction)
                          (implicit ec: ExecutionContext) = EssentialAction { implicit rh =>
    Iteratee.flatten {
      check(envelopeId).map {
        case Xor.Right("OPEN") => block(rh)
        case Xor.Right(otherStatus) =>
          logAndReturn(LOCKED, s"Unable to upload to envelope: $envelopeId with status: $otherStatus")
        case Xor.Left(EnvelopeStatusNotFoundError(_)) =>
          logAndReturn(NOT_FOUND, s"Unable to upload to nonexistent envelope: $envelopeId")
        case Xor.Left(error) =>
          logAndReturn(INTERNAL_SERVER_ERROR, error.toString)
      }
    }
  }

  private def logAndReturn(statusCode: Int, problem: String)(implicit rh: RequestHeader) = {
    Logger.warn(s"Request: $rh failed because: $problem")
    Done[Array[Byte], Result](new Status(statusCode).apply{ Json.obj("message" -> problem) })
  }
}
