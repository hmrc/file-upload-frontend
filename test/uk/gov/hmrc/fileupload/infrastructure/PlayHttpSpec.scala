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

package uk.gov.hmrc.fileupload.infrastructure

import cats.data.Xor
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.http.Fault
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import play.api.http.Status
import play.api.libs.json.{JsSuccess, Json}
import play.api.libs.ws.WS
import uk.gov.hmrc.fileupload.infrastructure.PlayHttp.PlayHttpError
import uk.gov.hmrc.play.audit.http.config.{AuditingConfig, BaseUri, Consumer}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global

class PlayHttpSpec extends UnitSpec with BeforeAndAfterAll with BeforeAndAfterEach with WithFakeApplication with Eventually {
  this: Suite =>

  private lazy val fakeDownstreamSystemConfig = wireMockConfig().port(8900)
  private lazy val fakeDownstreamSystem = new WireMockServer(fakeDownstreamSystemConfig)
  private val downstreamPath = "/test"
  private val downstreamUrl = s"http://localhost:${fakeDownstreamSystemConfig.portNumber()}$downstreamPath"

  private lazy val fakeAuditConsumerConfig = wireMockConfig().port(8901)
  private lazy val fakeAuditConsumer = new WireMockServer(fakeAuditConsumerConfig)

  private val consumer = Consumer(BaseUri("localhost", fakeAuditConsumerConfig.portNumber(), "http"))
  private val testAppName = "test-app"
  private val auditConnector = AuditConnector(AuditingConfig(Some(consumer), enabled = true, traceRequests = true))
  private var loggedErrors = ListBuffer.empty[Throwable]
  private val testExecute = PlayHttp.execute(auditConnector, testAppName, Some(t => {
    loggedErrors += t
  })) _

  override def beforeAll() = {
    super.beforeAll()
    fakeDownstreamSystem.start()
    fakeAuditConsumer.start()

    fakeAuditConsumer.addStubMapping(
      post(urlPathMatching("/*"))
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(Status.OK)).build())
  }


  override protected def beforeEach(): Unit = {
    super.beforeEach()

    fakeAuditConsumer.resetRequests()
  }

  override def afterAll() = {
    super.afterAll()
    fakeDownstreamSystem.stop()
    fakeAuditConsumer.stop()
  }

  "Executor" should {
    "execute and audit successful calls" in {
      testFor(Status.OK)
    }

    "execute and audit unsuccessful calls" in {
      testFor(Status.BAD_GATEWAY)
    }

    def testFor(statusCode: Int) = {
      fakeDownstreamSystem.addStubMapping(
        get(urlPathMatching(downstreamPath))
          .willReturn(new ResponseDefinitionBuilder()
            .withStatus(statusCode).withBody("someResponseBody"))
          .build())

      val response = await(testExecute(WS.url(downstreamUrl)(fakeApplication).withMethod("GET"))).valueOr(t => fail(t.message))

      response.status shouldBe statusCode
      eventually { getAudits.size() shouldBe 1}

      val auditedItem = getAudits.get(0)
      val json = Json.parse(auditedItem.getBodyAsString)

      (json \ "auditSource").validate[String] shouldBe JsSuccess[String](testAppName)
      (json \ "tags" \ "path").validate[String] shouldBe JsSuccess[String](downstreamPath)
      (json \ "tags" \ "method").validate[String] shouldBe JsSuccess[String]("GET")
      (json \ "tags" \ "statusCode").validate[String] shouldBe JsSuccess[String](s"$statusCode")
      (json \ "tags" \ "responseBody").validate[String] shouldBe JsSuccess[String]("someResponseBody")
    }

    def getAudits = fakeAuditConsumer.findAll(
      postRequestedFor(urlPathMatching("/*"))
    )

    "logs errors" in {
      fakeDownstreamSystem.addStubMapping(
        get(urlPathMatching(downstreamPath))
          .willReturn(new ResponseDefinitionBuilder()
            .withFault(Fault.MALFORMED_RESPONSE_CHUNK))
          .build())

      val response = await(testExecute(WS.url(downstreamUrl)(fakeApplication).withMethod("GET")))

      response shouldBe Xor.Left(PlayHttpError("Remotely Closed"))
      loggedErrors.headOption.getOrElse(fail("No error logged")).getMessage shouldBe "Remotely Closed"
    }
  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(5, Seconds))
}
