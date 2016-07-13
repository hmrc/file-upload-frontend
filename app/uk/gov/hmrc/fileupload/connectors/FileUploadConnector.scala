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

package uk.gov.hmrc.fileupload.connectors

import uk.gov.hmrc.fileupload.Errors.EnvelopeValidationError
import uk.gov.hmrc.fileupload.WSHttp
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait FileUploadConnector {
  self: ServicesConfig =>

  import scala.concurrent.ExecutionContext.Implicits.global

  val http: HttpGet = WSHttp
  val baseUrl: String = baseUrl("file-upload")

  def validate(envelopeId: String)(implicit hc: HeaderCarrier): Future[Try[String]] = {
    http.GET(s"$baseUrl/file-upload/envelope/$envelopeId").map {
      case r if r.status == 200 => Success(envelopeId)
      case _ => Failure(EnvelopeValidationError(envelopeId))
    }.recover { case _ => Failure(EnvelopeValidationError(envelopeId)) }
  }
}
