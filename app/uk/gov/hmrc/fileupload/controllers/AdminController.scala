/*
 * Copyright 2023 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.fileupload.{ApplicationModule, EnvelopeId, FileId, FileRefId}
import uk.gov.hmrc.fileupload.notifier.CommandHandler
import uk.gov.hmrc.fileupload.transfer.TransferRequested
import uk.gov.hmrc.fileupload.virusscan.VirusScanRequested
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AdminController @Inject()(
  appModule: ApplicationModule,
  mcc      : MessagesControllerComponents
)(implicit
  ec       : ExecutionContext
) extends FrontendController(mcc) {

  val commandHandler: CommandHandler = appModule.commandHandler

  def scan(envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId) = Action.async { request =>
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
    commandHandler.notify(
      VirusScanRequested(envelopeId = envelopeId, fileId = fileId, fileRefId = fileRefId)
    )
    Future.successful(Ok)
  }

  def transfer(envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId) = Action.async { request =>
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
    commandHandler.notify(
      TransferRequested(envelopeId = envelopeId, fileId = fileId, fileRefId = fileRefId)
    )
    Future.successful(Ok)
  }
}
