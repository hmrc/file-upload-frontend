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

package uk.gov.hmrc.fileupload.quarantine

import cats.data.Xor
import play.api.libs.json.JsString
import uk.gov.hmrc.fileupload.fileupload._
import uk.gov.hmrc.fileupload.{EnvelopeId, File, FileId, FileReferenceId}

import scala.concurrent.{ExecutionContext, Future}

object QuarantineService {

  type QuarantineDownloadResult = Xor[QuarantineDownloadFileNotFound.type, File]
  case object QuarantineDownloadFileNotFound

  def getFileFromQuarantine(retrieveFile: (FileReferenceId) => Future[Option[FileData]])
                           (fileReferenceId: FileReferenceId)
                           (implicit executionContext: ExecutionContext): Future[QuarantineDownloadResult] =
    for {
      maybeFileData <- retrieveFile(fileReferenceId)
    } yield
      Xor.fromOption(maybeFileData, ifNone = QuarantineDownloadFileNotFound).map { fd =>
        File(data = fd.data, length = fd.length, filename = fd.filename, contentType = fd.contentType)
      }
}
