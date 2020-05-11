/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.s3

import java.io.InputStream

import akka.stream.scaladsl.Sink
import akka.util.ByteString
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{BodyParser, BodyParsers, MultipartFormData}
import play.core.parsers.Multipart.{FileInfo, FilePartHandler}

import scala.concurrent.ExecutionContext

object InMemoryMultipartFileHandler {

  type InMemoryMultiPartBodyParser = () => BodyParser[MultipartFormData[FileCachedInMemory]]

  case class FileCachedInMemory(data: ByteString) {
    def size: Int = data.size
    def inputStream: InputStream = data.iterator.asInputStream
  }

  def cacheFileInMemory(implicit ec: ExecutionContext): FilePartHandler[FileCachedInMemory] = {
    case FileInfo(partName, filename, contentType) =>
      Accumulator(Sink.fold[ByteString, ByteString](ByteString.empty)(_ ++ _)).map { fullFile =>
        FilePart(partName, filename, contentType, FileCachedInMemory(fullFile))
      }
  }

  def parser(implicit ec: ExecutionContext): InMemoryMultiPartBodyParser = {
    () => BodyParsers.parse.multipartFormData(cacheFileInMemory)
  }

}
