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

import play.api.mvc.{Action, Controller}
import uk.gov.hmrc.fileupload.notifier.NotifierService._
import uk.gov.hmrc.fileupload.transfer.TransferRequested
import uk.gov.hmrc.fileupload.virusscan.VirusScanRequested
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}

import scala.concurrent.{ExecutionContext, Future}

class AdminController (notify: AnyRef => Future[NotifyResult])
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
