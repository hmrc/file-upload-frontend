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

import akka.util.ByteString
import play.api.libs.iteratee.Iteratee
import play.api.libs.streams.{Accumulator, Streams}
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{BodyParser, MultipartFormData}
import play.core.parsers.Multipart
import play.core.parsers.Multipart.{FileInfo, FilePartHandler}
import uk.gov.hmrc.fileupload.fileupload.JSONReadFile
import uk.gov.hmrc.fileupload.utils.StreamsConverter

import scala.concurrent.{ExecutionContext, Future}

object UploadParser {

  def parse(writeFile: (String, Option[String]) => Iteratee[Array[Byte], Future[JSONReadFile]])
           (implicit ex: ExecutionContext): BodyParser[MultipartFormData[Future[JSONReadFile]]] = {

    play.api.mvc.BodyParsers.parse.multipartFormData(handleFilePart {
      case Multipart.FileInfo(partName, filename, contentType) =>
        StreamsConverter.iterateeToAccumulator(writeFile(filename, contentType))
    })
  }

  def handleFilePart[A](handler: FileInfo => Accumulator[ByteString, A]): FilePartHandler[A] = {
    case FileInfo(partName, fileName, contentType) =>
      val safeFileName = fileName.split('\\').takeRight(1).mkString
      import play.api.libs.iteratee.Execution.Implicits.trampoline
      handler(FileInfo(partName, safeFileName, contentType)).map(a => FilePart(partName, safeFileName, contentType, a))
  }
}
