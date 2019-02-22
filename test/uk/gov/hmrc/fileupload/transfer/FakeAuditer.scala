/*
 * Copyright 2019 HM Revenue & Customs
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
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Suite}
import play.api.http.Status

trait FakeAuditer extends BeforeAndAfterAll with ScalaFutures {
  this: Suite =>

  val config = wireMockConfig().dynamicPort()

  lazy val fakeAuditer = new WireMockServer(config)

  override def beforeAll() = {
    super.beforeAll()
    fakeAuditer.start()

    fakeAuditer.addStubMapping(
      post(urlPathMatching("/*"))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(Status.OK)).build())

  }

  override def afterAll() = {
    super.afterAll()
    fakeAuditer.stop()
  }

  def resetAuditRequests() = fakeAuditer.resetRequests()

  def getAudits() = fakeAuditer.findAll(
    postRequestedFor(urlPathMatching("/*"))
  )
}
