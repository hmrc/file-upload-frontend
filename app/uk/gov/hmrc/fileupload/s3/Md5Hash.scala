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

import java.security.MessageDigest
import java.util.Base64

import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.util.ByteString

import scala.concurrent.{ExecutionContext, Future}

object Md5Hash:
  def md5HashSink(using ExecutionContext): Sink[ByteString, Future[String]] =
    val md = MessageDigest.getInstance("MD5")
    Sink
      .foreach[ByteString](bs => md.update(bs.toArray))
      .mapMaterializedValue(_.map(_ => Base64.getEncoder.encodeToString(md.digest())))

  def md5Hash(data: ByteString): String =
    val md = MessageDigest.getInstance("MD5")
    md.update(data.toArray)
    Base64.getEncoder.encodeToString(md.digest())
