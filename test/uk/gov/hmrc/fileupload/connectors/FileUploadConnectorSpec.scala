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

import scala.util.{Failure, Success}

class FileUploadConnectorSpec extends WireMockSpec {
  override lazy val mappings:MappingsLoader = new JsonFileMappingsLoader(new SingleRootFileSource("test/resources/mappings"))

  implicit val hc = HeaderCarrier()

  "The fileUploadConnector" should {
    "result in a ValidEnvelope response for a valid envelopeId" in new TestFileUploadConnector(wiremockBaseUrl) {
      await(validate("envelopeId")) should be (Success("envelopeId"))
    }

    "result in an InvalidEnvelope response for an invalid envelopeId" in new TestFileUploadConnector(wiremockBaseUrl) {
      await(validate("invalidId")) should be (Failure(EnvelopeValidationError("invalidId")))
    }

    "is populated with a baseURL from constituent parts in configuration" in {
      val connector = new FileUploadConnector with ServicesConfig {}
      connector.baseUrl should be ("http://localhost:8898")
    }
  }

  class TestFileUploadConnector(url:String) extends FileUploadConnector with ServicesConfig {
    override val baseUrl: String = url
    override val http: HttpGet = WSHttp
  }
}
