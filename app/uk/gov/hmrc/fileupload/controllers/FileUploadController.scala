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

import play.api.libs.iteratee.{Concurrent, Iteratee}
import play.api.mvc.BodyParsers.parse.{Multipart, _}
import play.api.mvc.{Action, Results}

import scala.concurrent.{ExecutionContext, Future}

class FileUploadController()(implicit executionContext: ExecutionContext)  {

  def enumeratorBodyParser = multipartFormData(Multipart.handleFilePart {
    case Multipart.FileInfo(partName, filename, contentType) =>
      val (enum, channel) = Concurrent.broadcast[Array[Byte]]

      Iteratee.foreach[Array[Byte]] { channel.push }.map { _ =>
        channel.eofAndEnd()
        enum
      }
  })

  def upload() = Action.async(enumeratorBodyParser) { implicit request =>
    Future.successful(Results.SeeOther(request.body.dataParts("successRedirect").head))
  }
}
