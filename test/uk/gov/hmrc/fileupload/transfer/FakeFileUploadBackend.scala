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

package uk.gov.hmrc.fileupload.transfer

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.{BeforeAndAfterAll, Suite}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

trait FakeFileUploadBackend extends BeforeAndAfterAll {
  this: Suite =>

  lazy val fileUploadBackendPort = 8080

  private lazy val server = new WireMockServer(wireMockConfig().port(fileUploadBackendPort))

  final lazy val fileUploadBackendBaseUrl = s"http://localhost:$fileUploadBackendPort"

  override def beforeAll() = {
    super.beforeAll()
    server.start()
  }

  override def afterAll() = {
    super.afterAll()
    server.stop()
  }

  def responseToUpload(envelopeId: EnvelopeId, fileId: FileId, status: Int = 200, body: String = ""): Unit = {
    server.addStubMapping(
      put(urlPathMatching(s"/file-upload/envelope/${envelopeId.value}/file/${fileId.value}/content"))
        .willReturn(new ResponseDefinitionBuilder()
          .withBody(body)
          .withStatus(status))
        .build())
  }

  def respondToEnvelopeCheck(envelopeId: EnvelopeId, status: Int = 200, body: String = ""): Unit = {
    server.addStubMapping(
      get(urlPathMatching(s"/file-upload/envelope/${envelopeId.value}"))
        .willReturn(new ResponseDefinitionBuilder()
          .withBody(body)
          .withStatus(status))
        .build())
  }
}
