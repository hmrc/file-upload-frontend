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

import play.api.mvc.{MessagesControllerComponents, RequestHeader}
import uk.gov.hmrc.fileupload.{ApplicationModule, EnvelopeId, FileId, FileRefId}
import uk.gov.hmrc.fileupload.notifier.CommandHandler
import uk.gov.hmrc.fileupload.transfer.TransferRequested
import uk.gov.hmrc.fileupload.virusscan.VirusScanRequested
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class AdminController @Inject()(
  appModule: ApplicationModule,
  mcc      : MessagesControllerComponents
)(using ExecutionContext
) extends FrontendController(mcc):

  val commandHandler: CommandHandler =
    appModule.commandHandler

  def scan(envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId) =
    Action { implicit request: RequestHeader =>
      commandHandler.notify:
        VirusScanRequested(envelopeId = envelopeId, fileId = fileId, fileRefId = fileRefId)
      Ok
    }

  def transfer(envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId) =
    Action { implicit request: RequestHeader =>
      commandHandler.notify:
        TransferRequested(envelopeId = envelopeId, fileId = fileId, fileRefId = fileRefId)
      Ok
    }
