/*
 * Copyright 2020 HM Revenue & Customs
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

import akka.stream.Materializer
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.http.Fault
import org.scalatest.{BeforeAndAfterEach, EitherValues, OptionValues, Suite}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.http.Status
import play.api.libs.json.{JsSuccess, Json}
import play.api.libs.ws.WSClient
import uk.gov.hmrc.fileupload.TestApplicationComponents
import uk.gov.hmrc.fileupload.infrastructure.PlayHttp.PlayHttpError
import uk.gov.hmrc.fileupload.transfer.FakeAuditer
import uk.gov.hmrc.play.audit.http.config.{AuditingConfig, BaseUri, Consumer}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import akka.stream.Materializer
import play.api.inject.ApplicationLifecycle

class PlayHttpSpec
  extends AnyWordSpecLike
     with Matchers
     with OptionValues
     with EitherValues
     with BeforeAndAfterEach
     with TestApplicationComponents
     with Eventually
     with FakeAuditer
     with IntegrationPatience {
  this: Suite =>

  private lazy val fakeDownstreamSystemConfig = wireMockConfig().dynamicPort()
  private lazy val fakeDownstreamSystem = new WireMockServer(fakeDownstreamSystemConfig)
  private lazy val downstreamPath = "/test"
  private lazy val downstreamUrl = s"http://localhost:${fakeDownstreamSystem.port()}$downstreamPath"

  private val testAppName = "test-app"

  object TestAuditConnector extends AuditConnector {
    override def materializer: Materializer =
      app.injector.instanceOf[Materializer]

    override def lifecycle: ApplicationLifecycle =
      app.injector.instanceOf[ApplicationLifecycle]

    override lazy val consumer = Consumer(BaseUri("localhost", fakeAuditer.port(), "http"))
    override lazy val auditingConfig = AuditingConfig(Some(consumer), enabled = true, auditSource = "test-app")
  }

  private val loggedErrors = ListBuffer.empty[Throwable]
  private val testExecute = PlayHttp.execute(TestAuditConnector, testAppName, Some { t => loggedErrors += t } ) _

  override def beforeAll(): Unit = {
    super.beforeAll()
    fakeDownstreamSystem.start()
  }


  override protected def beforeEach(): Unit = {
    super.beforeEach()
    resetAuditRequests()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    fakeDownstreamSystem.stop()
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
            .withStatus(statusCode).withBody("someResponseBody")
          )
          .build()
      )

      val wsClient = app.injector.instanceOf[WSClient]
      val response = testExecute(wsClient.url(downstreamUrl).withMethod("GET")).futureValue.right.value

      response.status shouldBe statusCode
      eventually {
        getAudits().size() shouldBe 1
      }

      val auditedItem = getAudits().get(0)
      val json = Json.parse(auditedItem.getBodyAsString)

      (json \ "auditSource").validate[String] shouldBe JsSuccess[String](testAppName)
      (json \ "tags" \ "path").validate[String] shouldBe JsSuccess[String](downstreamPath)
      (json \ "tags" \ "method").validate[String] shouldBe JsSuccess[String]("GET")
      (json \ "tags" \ "statusCode").validate[String] shouldBe JsSuccess[String](s"$statusCode")
      (json \ "tags" \ "responseBody").validate[String] shouldBe JsSuccess[String]("someResponseBody")
    }

    "logs errors" in {
      fakeDownstreamSystem.addStubMapping(
        get(urlPathMatching(downstreamPath))
          .willReturn(new ResponseDefinitionBuilder()
            .withFault(Fault.MALFORMED_RESPONSE_CHUNK)
          )
          .build()
      )

      val wsClient = app.injector.instanceOf[WSClient]
      val response = testExecute(wsClient.url(downstreamUrl).withMethod("GET")).futureValue

      response shouldBe Left(PlayHttpError("Remotely closed"))
      loggedErrors.headOption.getOrElse(fail("No error logged")).getMessage shouldBe "Remotely closed"
    }
  }
}
