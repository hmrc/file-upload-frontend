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

package uk.gov.hmrc.fileupload.connectors

import com.github.tomakehurst.wiremock.common.SingleRootFileSource
import com.github.tomakehurst.wiremock.standalone.{JsonFileMappingsLoader, MappingsLoader}
import uk.gov.hmrc.fileupload.Errors.EnvelopeValidationError
import uk.gov.hmrc.fileupload.{WSHttp, WireMockSpec}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet}
import uk.gov.hmrc.fileupload.UploadFixtures._

import scala.concurrent.Future
import scala.util.{Failure, Success}

class FileUploadConnectorSpec extends WireMockSpec {
  override lazy val mappings:MappingsLoader = new JsonFileMappingsLoader(new SingleRootFileSource("test/resources/mappings"))

  implicit val hc = HeaderCarrier()

  "The fileUploadConnector" should {
    "result in a ValidEnvelope response for a valid envelopeId" in new TestFileUploadConnector {
      await(validate(validEnvelopeId)) should be (Success(validEnvelopeId))
    }

    "result in an InvalidEnvelope response for an invalid envelopeId" in new TestFileUploadConnector {
      await(validate("invalidId")) should be (Failure(EnvelopeValidationError("invalidId")))
    }

    "result in an InvalidEnvelope response for a sealed envelopeId" in new TestFileUploadConnector {
      await(validate("2f816e24-1316-408d-aa2c-ba188c2090d9")) should be (Failure(EnvelopeValidationError("2f816e24-1316-408d-aa2c-ba188c2090d9")))
    }

    "is populated with a baseURL from constituent parts in configuration" in {
      val connector = new FileUploadConnector with ServicesConfig {}
      connector.baseUrl should be ("http://localhost:8898")
    }

    "result in a Success[Boolean] for a valid file upload" in new TestFileUploadConnector {
      pending

      val fileData = FileData(data = testFile, name = "TEST.out", contentType = "text/plain", envelopeId = "fea8cc15-f2d1-4eb5-b10e-b892bcbe94f8", fileId = "1")

      await(putFile(fileData)) should be (Success(true))
    }

    "result in a Failure[Boolean] for an invalid file upload" in new TestFileUploadConnector {
      pending

      val fileData = FileData(data = testFile, name = "TEST.out", contentType = "text/plain", envelopeId = "invalidId", fileId = "1")

      await(putFile(fileData)) should be (Failure(EnvelopeValidationError("invalidId")))
    }
  }

  trait TestFileUploadConnector extends FileUploadConnector with ServicesConfig {
    override val baseUrl = wiremockBaseUrl
  }
}
