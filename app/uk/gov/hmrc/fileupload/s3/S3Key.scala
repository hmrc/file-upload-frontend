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

package uk.gov.hmrc.fileupload.s3

import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

object S3Key:
  def forEnvSubdir(envSubdir: String): (EnvelopeId, FileId) => S3KeyName =
    (e: EnvelopeId, f: FileId) => S3KeyName(s"$envSubdir/$e/$f")

  def forZipSubdir(zipSubdir: String)(zipId: String): S3KeyName =
    S3KeyName(s"$zipSubdir/$zipId")

case class S3KeyName(value: String) extends AnyVal:
  override def toString: String = value
