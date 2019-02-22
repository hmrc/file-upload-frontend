/*
 * Copyright 2018 HM Revenue & Customs
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
import uk.gov.hmrc.fileupload._
import scala.concurrent.{ExecutionContext, Future}

object QuarantineService {

  type QuarantineDownloadResult = Xor[QuarantineDownloadFileNotFound.type, File]
  case object QuarantineDownloadFileNotFound

  def getFileFromQuarantine(retrieveFile: (String, String) => Future[Option[FileData]])
                           (key: String, version: String)
                           (implicit executionContext: ExecutionContext): Future[QuarantineDownloadResult] =
    for {
      maybeFileData <- retrieveFile(key, version)
    } yield
      Xor.fromOption(maybeFileData, ifNone = QuarantineDownloadFileNotFound).map { fd =>
        File(data = fd.data, length = fd.length, filename = fd.filename, contentType = fd.contentType)
      }
}
