/*
 * Copyright 2021 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import org.scalatest.{BeforeAndAfterEach, EitherValues, OptionValues, Suite}
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsSuccess, Json}
import play.api.libs.ws.WSClient
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.fileupload.TestApplicationComponents
import uk.gov.hmrc.fileupload.infrastructure.PlayHttp.PlayHttpError
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global

class PlayHttpSpec
  extends AnyWordSpecLike
     with Matchers
     with OptionValues
     with EitherValues
     with BeforeAndAfterEach
     with TestApplicationComponents
     with Eventually
     with ScalaFutures
     with IntegrationPatience
     with WireMockSupport {
  this: Suite =>

  private lazy val downstreamPath = "/test"
  private lazy val downstreamUrl = s"http://$wireMockHost:$wireMockPort$downstreamPath"

  private val testAppName = "test-app"

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .configure(
        "appName"                            -> testAppName,
        "auditing.enabled"                   -> true,
        "auditing.consumer.baseUri.host"     -> wireMockHost,
        "auditing.consumer.baseUri.port"     -> wireMockPort,
        "auditing.consumer.baseUri.protocol" -> "http",
      )
      .build()

  private lazy val TestAuditConnector =
    app.injector.instanceOf[AuditConnector]

  private val wsClient = app.injector.instanceOf[WSClient]

  private val hc = HeaderCarrier()

  private val loggedErrors = ListBuffer.empty[Throwable]
  private val testExecute = PlayHttp.execute(TestAuditConnector, testAppName, Some { t => loggedErrors += t } ) _

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    wireMockServer.resetRequests()
  }

  "Executor" should {
    "execute and audit successful calls" in {
      testFor(Status.OK)
    }

    "execute and audit unsuccessful calls" in {
      testFor(Status.BAD_GATEWAY)
    }

    def testFor(statusCode: Int) = {
      wireMockServer.addStubMapping(
        get(urlPathMatching(downstreamPath))
          .willReturn(new ResponseDefinitionBuilder()
            .withStatus(statusCode).withBody("someResponseBody")
          )
          .build()
      )

      val response =
        testExecute(
          wsClient.url(downstreamUrl).withMethod("GET"),
          hc
        ).futureValue.right.value

      response.status shouldBe statusCode
      eventually {
        getAudits().size() shouldBe 1
      }

      val auditedItem = getAudits().get(0)
      val json = Json.parse(auditedItem.getBodyAsString)

      (json \ "auditSource"          ).validate[String] shouldBe JsSuccess[String](testAppName)
      (json \ "tags" \ "path"        ).validate[String] shouldBe JsSuccess[String](downstreamPath)
      (json \ "tags" \ "method"      ).validate[String] shouldBe JsSuccess[String]("GET")
      (json \ "tags" \ "statusCode"  ).validate[String] shouldBe JsSuccess[String](s"$statusCode")
      (json \ "tags" \ "responseBody").validate[String] shouldBe JsSuccess[String]("someResponseBody")
    }

    "logs errors" in {
      wireMockServer.addStubMapping(
        get(urlPathMatching(downstreamPath))
          .willReturn(new ResponseDefinitionBuilder()
            .withFault(Fault.MALFORMED_RESPONSE_CHUNK)
          )
          .build()
      )

      val response =
        testExecute(
          wsClient.url(downstreamUrl).withMethod("GET"),
          hc
        ).futureValue

      response shouldBe Left(PlayHttpError("Remotely closed"))
      loggedErrors.headOption.getOrElse(fail("No error logged")).getMessage shouldBe "Remotely closed"
    }
  }

  def getAudits() =
    wireMockServer.findAll(
      postRequestedFor(urlPathMatching("/*"))
    )
}
