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

package uk.gov.hmrc.fileupload.support

import org.scalatest.Suite
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.fileupload.virusscan.AvClient
import uk.gov.hmrc.play.test.UnitSpec

trait IntegrationTestApplicationComponents
  extends UnitSpec
     with GuiceOneServerPerSuite
     with FakeFileUploadBackend {
  this: Suite =>

  lazy val avClient: Option[AvClient] = None
  lazy val disableAvScanning: Boolean = true
  lazy val numberOfTimeoutAttempts: Int = 1

  val conf =
    Seq(
      "auditing.enabled" -> "false",
      "Test.clam.antivirus.runStub" -> "true",
      "Test.clam.antivirus.disableScanning" -> disableAvScanning.toString,
      "Test.clam.antivirus.numberOfTimeoutAttempts" -> numberOfTimeoutAttempts.toString,
      "Test.microservice.services.file-upload-backend.port" -> backendPort.toString,
      "aws.service_endpoint" -> "http://127.0.0.1:8001",
      "aws.s3.bucket.upload.quarantine" -> "file-upload-quarantine",
      "aws.s3.bucket.upload.transient" -> "file-upload-transient",
      "aws.access.key.id" -> "ENTER YOUR KEY",
      "aws.secret.access.key" -> "ENTER YOUR SECRET KEY"
    )

  // creates a new application and sets the components
  implicit override lazy val app: Application = {
    val builder =
      new GuiceApplicationBuilder()
        .configure(conf: _*)
    avClient
      .fold(builder)(avClient => builder.overrides(bind(classOf[AvClient]).toInstance(avClient)))
      .build()
  }
}
