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

import uk.gov.hmrc.fileupload.WSHttp
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpResponse}

import scala.concurrent.Future

object FileUploadConnector extends FileUploadConnector with ServicesConfig {
  override val http = WSHttp
  override val baseUrl:String = baseUrl("file-upload")
}

trait FileUploadConnector extends EnvelopeValidator

trait EnvelopeValidator {
  import scala.concurrent.ExecutionContext.Implicits.global

  val baseUrl: String
  val http: HttpGet

  def validate(envelopeId: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    http.GET(s"$baseUrl/envelope/$envelopeId").map { statusIsOk }.recover { case _ => false }
  }

  def statusIsOk(resp:HttpResponse) = resp.status == 200
}
