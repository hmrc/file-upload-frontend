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

package uk.gov.hmrc.fileupload

import java.util.concurrent.TimeUnit

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import com.github.tomakehurst.wiremock.standalone.MappingsLoader
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.OneServerPerSuite
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration.FiniteDuration

trait WireMockSpec extends UnitSpec with BeforeAndAfterAll with BeforeAndAfterEach with ScalaFutures with OneServerPerSuite {
  override implicit val defaultTimeout = FiniteDuration(100, TimeUnit.SECONDS)

  private val WIREMOCK_PORT = 21212
  private val stubHost = "localhost"

  protected val wiremockBaseUrl: String = s"http://localhost:$WIREMOCK_PORT"
  protected val wireMockServer = new WireMockServer(wireMockConfig().port(WIREMOCK_PORT))

  val mappings: MappingsLoader

  override def beforeAll() = {
    wireMockServer.stop()
    wireMockServer.start()
    WireMock.configureFor(stubHost, WIREMOCK_PORT)
  }

  override def afterAll() = {
    wireMockServer.stop()
  }

  override def beforeEach() = {
    WireMock.reset()
    wireMockServer.loadMappingsUsing(mappings)
  }
}
