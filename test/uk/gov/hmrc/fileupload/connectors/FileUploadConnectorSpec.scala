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
import uk.gov.hmrc.fileupload.{WSHttp, WireMockSpec}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet}

class FileUploadConnectorSpec extends WireMockSpec {
  override lazy val mappings:MappingsLoader = new JsonFileMappingsLoader(new SingleRootFileSource("test/resources/mappings"))

  "The fileUploadConnector" should {
    "result in a ValidEnvelope response for a valid envelopeId" in new TestFileUploadConnector(wiremockBaseUrl) {
      retrieveEnvelope("envelopeId") shouldBe ValidEnvelope(id = "envelopeId", fileIds = Seq("12345"))
    }

    "result in an InvalidEnvelope response for an invalid envelopeId" in new TestFileUploadConnector(wiremockBaseUrl) {
      retrieveEnvelope("invalidId") shouldBe InvalidEnvelope
    }
  }

  class TestFileUploadConnector(baseUrl:String) extends FileUploadConnector {
    import scala.concurrent.ExecutionContext.Implicits.global

    override def validate(envelopeId: String): Boolean = {
      implicit val hc = HeaderCarrier()

      val http: HttpGet = WSHttp

      http.GET(s"$baseUrl/$envelopeId").map { _.status match {
          case 200 => true
          case _ => false
        }
      }.recoverWith { case _ => false }
    }
  }
}

