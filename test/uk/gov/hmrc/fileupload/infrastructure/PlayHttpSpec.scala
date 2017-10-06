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

package uk.gov.hmrc.fileupload.infrastructure

import cats.data.Xor
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.http.Fault
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import play.api.http.Status
import play.api.libs.json.{JsSuccess, Json}
import play.api.libs.ws.WSRequest
import uk.gov.hmrc.fileupload.TestApplicationComponents
import uk.gov.hmrc.fileupload.infrastructure.PlayHttp.PlayHttpError
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.config.{AuditingConfig, BaseUri, Consumer}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.RunMode
import uk.gov.hmrc.play.test.UnitSpec

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global

class PlayHttpSpec extends UnitSpec with TestApplicationComponents with Eventually {
  this: Suite =>

  private val testAppName = "test-app"

  case class FakeServers(downstream: WireMockServer,
                         auditer: WireMockServer,
                         auditConnector: AuditConnector) {
    def getAudits = auditer.findAll(
      postRequestedFor(urlPathMatching("/*"))
    )
  }

  def withFakeServers[T](f: FakeServers => T): T = {
    val downstreamConfig = wireMockConfig().dynamicPort()
    val downstream = new WireMockServer(downstreamConfig)

    val auditerConfig = wireMockConfig().dynamicPort()
    val auditer = new WireMockServer(auditerConfig)

    try {
      downstream.start()
      auditer.start()
      auditer.addStubMapping(
          post(urlPathMatching("/*"))
            .willReturn(new ResponseDefinitionBuilder()
              .withStatus(Status.OK)).build())

      val auditConnector = new AuditConnector {
        override def auditingConfig: AuditingConfig =
          AuditingConfig(Some(Consumer(BaseUri("localhost", auditer.port(), "http"))), enabled = true)
      }
      f(FakeServers(downstream, auditer, auditConnector))
    }
    finally {
      downstream.stop()
      auditer.stop()
    }
  }

  "Executor" should {
    "execute and audit successful calls" in {
      testFor(Status.OK)
    }

    "execute and audit unsuccessful calls" in {
      testFor(Status.BAD_GATEWAY)
    }

    def testFor(statusCode: Int) = {
      val downstreamPath = "/test"

      withFakeServers { fakeServers =>
        fakeServers.downstream.addStubMapping(
          get(urlPathMatching(downstreamPath))
            .willReturn(new ResponseDefinitionBuilder()
              .withStatus(statusCode).withBody("someResponseBody"))
            .build())

        val testExecute = PlayHttp.execute(fakeServers.auditConnector, testAppName, None) _

        val downstreamUrl = s"http://localhost:${fakeServers.downstream.port()}$downstreamPath"
        val response = await(testExecute(components.wsClient.url(downstreamUrl).withMethod("GET"))).valueOr(t => fail(t.message))

        response.status shouldBe statusCode
        eventually {
          fakeServers.getAudits.size() shouldBe 1
        }

        val auditedItem = fakeServers.getAudits.get(0)
        val json = Json.parse(auditedItem.getBodyAsString)

        (json \ "auditSource").validate[String] shouldBe JsSuccess[String](testAppName)
        (json \ "tags" \ "path").validate[String] shouldBe JsSuccess[String](downstreamPath)
        (json \ "tags" \ "method").validate[String] shouldBe JsSuccess[String]("GET")
        (json \ "tags" \ "statusCode").validate[String] shouldBe JsSuccess[String](s"$statusCode")
        (json \ "tags" \ "responseBody").validate[String] shouldBe JsSuccess[String]("someResponseBody")
      }
    }

    "logs errors" in {
      val downstreamPath = "/test"

      withFakeServers { fakeServers =>
        val downstreamUrl = s"http://localhost:${fakeServers.downstream.port()}$downstreamPath"

        fakeServers.downstream.addStubMapping(
          get(urlPathMatching(downstreamPath))
            .willReturn(new ResponseDefinitionBuilder()
              .withFault(Fault.MALFORMED_RESPONSE_CHUNK))
            .build())

        val loggedErrors = new ListBuffer[Throwable]
        val testExecute = PlayHttp.execute(fakeServers.auditConnector, testAppName, Some(t => {
          loggedErrors += t
        })) _

        val response = await(testExecute(components.wsClient.url(downstreamUrl).withMethod("GET")))

        response shouldBe Xor.Left(PlayHttpError("Remotely closed"))
        loggedErrors.headOption.getOrElse(fail("No error logged")).getMessage shouldBe "Remotely closed"
      }
    }
  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(5, Seconds))
}
