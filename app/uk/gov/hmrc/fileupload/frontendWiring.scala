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

package uk.gov.hmrc.fileupload

import akka.actor.ActorSystem
import com.typesafe.config.Config
import play.api.Mode.Mode
import play.api.{Configuration, Play}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.{HttpDelete, HttpGet, HttpPost, HttpPut}
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector => Auditing}
import uk.gov.hmrc.play.config.{AppName, RunMode, ServicesConfig}
import uk.gov.hmrc.play.frontend.config.LoadAuditingConfig
import uk.gov.hmrc.play.http.ws.{WSDelete, WSGet, WSPost, WSPut}

object FrontendAuditConnector extends Auditing with AppName {
  override lazy val auditingConfig = LoadAuditingConfig(s"auditing")

  override protected def appNameConfiguration: Configuration = Play.current.configuration
}

object WSHttp extends HttpGet with WSGet with HttpPut with WSPut with HttpPost with WSPost with HttpDelete with WSDelete with AppName with RunMode {
  override val hooks: Seq[HttpHook] = NoneRequired

  override protected def appNameConfiguration: Configuration = Play.current.configuration

  override protected def mode: Mode = Play.current.mode

  override protected def runModeConfiguration: Configuration = Play.current.configuration

  override protected def actorSystem: ActorSystem = Play.current.actorSystem

  override protected def configuration: Option[Config] = Option(Play.current.configuration.underlying)
}

object FrontendAuthConnector extends PlayAuthConnector with ServicesConfig {
  val serviceUrl = baseUrl("auth")
  lazy val http = WSHttp

  override protected def mode: Mode = Play.current.mode

  override protected def runModeConfiguration: Configuration = Play.current.configuration
}

