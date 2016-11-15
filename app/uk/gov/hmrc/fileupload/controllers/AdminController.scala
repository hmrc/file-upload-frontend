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

import play.api.libs.json.{JsObject, JsString, Json}
import play.api.mvc.{Action, BodyParser, Controller, MultipartFormData}
import uk.gov.hmrc.fileupload.fileupload._
import uk.gov.hmrc.fileupload.notifier.NotifierService._
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}
import uk.gov.hmrc.fileupload.transfer.TransferRequested
import uk.gov.hmrc.fileupload.virusscan.VirusScanRequested

import scala.concurrent.{ExecutionContext, Future}

class AdminController (uploadParser: () => BodyParser[MultipartFormData[Future[JSONReadFile]]],
                          notify: AnyRef => Future[NotifyResult],
                          now: () => Long)
                         (implicit executionContext: ExecutionContext) extends Controller {

  def scan(envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId) = Action.async { request =>
    notify(VirusScanRequested(envelopeId = envelopeId, fileId = fileId, fileRefId = fileRefId))
    Future.successful(Ok)
  }

  def transfer(envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId) = Action.async { request =>
    notify(TransferRequested(envelopeId = envelopeId, fileId = fileId, fileRefId = fileRefId))
    Future.successful(Ok)
  }
}

object AdminController {

  def metadataAsJson(formData: MultipartFormData[Future[JSONReadFile]]): JsObject = {
    val metadataParams = formData.dataParts.collect {
      case (key, singleValue :: Nil) => key -> JsString(singleValue)
      case (key, values: Seq[String]) if values.nonEmpty => key -> Json.toJson(values)
    }

    val metadata = if(metadataParams.nonEmpty) {
      Json.toJson(metadataParams).as[JsObject]
    } else {
      Json.obj()
    }

    metadata
  }
}
