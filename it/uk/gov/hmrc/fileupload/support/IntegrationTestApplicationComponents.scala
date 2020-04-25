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
import org.scalatestplus.play.OneServerPerSuite
import play.api.ApplicationLoader.Context
import play.api._
import play.api.mvc.EssentialFilter
import uk.gov.hmrc.clamav.ClamAntiVirus
import uk.gov.hmrc.clamav.config.ClamAvConfig
import uk.gov.hmrc.fileupload.ApplicationModule
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

trait IntegrationTestApplicationComponents extends UnitSpec with OneServerPerSuite
                                                            with FakeFileUploadBackend {
  this: Suite =>

  override implicit lazy val app: Application = components.application

  override lazy val port: Int = 9000

  lazy val clamAntiVirusTestClient: ClamAvConfig => ClamAntiVirus = ClamAntiVirus(_)
  lazy val disableAvScanning: Boolean = true
  lazy val numberOfTimeoutAttempts: Int = 1

  // accessed to get the components in tests
  lazy val components: ApplicationModule =
    new IntegrationTestApplicationModule(context, clamAntiVirusTestClient)

  lazy val context: ApplicationLoader.Context = {
    val classLoader = ApplicationLoader.getClass.getClassLoader
    val env = new Environment(new java.io.File("."), classLoader, Mode.Test)
    ApplicationLoader.createContext(env, initialSettings = Map(
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
    ))
  }

}

class IntegrationTestApplicationModule(context: Context, clamAntiVirusTestClient: ClamAvConfig => ClamAntiVirus) extends ApplicationModule(context = context) {
  override lazy val httpFilters: Seq[EssentialFilter] = Seq()
  override lazy val clamAvClient: ClamAvConfig => ClamAntiVirus = clamAntiVirusTestClient
}
