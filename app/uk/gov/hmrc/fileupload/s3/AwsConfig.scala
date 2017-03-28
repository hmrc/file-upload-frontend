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

package uk.gov.hmrc.fileupload.s3

import com.typesafe.config.ConfigFactory
import play.api.Environment

class AwsConfig(env: Environment) {
  protected val config = ConfigFactory.load()
  def mode = play.api.Play.current.mode
  def quarantineBucketName: String = config.getString("aws.s3.bucket.upload.quarantine")
  def transientBucketName: String = config.getString("aws.s3.bucket.upload.transient")
  def accessKeyId: String = config.getString("aws.access.key.id")
  def secretAccessKey: String = config.getString("aws.secret.access.key")
  def envSubdir: String = config.getString("aws.s3.bucket.env-subdir")
  def endpoint: Option[String] = {
    val path = s"${env.mode}.aws.service_endpoint"
    if (config.hasPath(path)) {
      Some(config.getString(path))
    } else
      None
  }
}
