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

package uk.gov.hmrc.fileupload.utils

import play.api.{Application, GlobalSettings, Logger, Play}
import play.api.mvc.{EssentialAction, Filters}
import play.filters.headers.SecurityHeadersFilter
import uk.gov.hmrc.play.audit.http.config.ErrorAuditingSettings
import uk.gov.hmrc.play.frontend.bootstrap.Routing.RemovingOfTrailingSlashes
import uk.gov.hmrc.play.frontend.bootstrap.{FrontendFilters, Routing}
import uk.gov.hmrc.play.frontend.filters.{DeviceIdCookieFilter, SecurityHeadersFilterFactory}
import uk.gov.hmrc.play.graphite.GraphiteConfig

/**
  * Copy from frontend-bootstrap_ 2.11-6.7.0.jar.uk.gov.hmrc.play.frontend.bootstrap.DefaultFrontendGlobal.scala
  * but without extends the ShowErrorPage.
  * Because the ShowErrorPage is not fit with our require.
  */

abstract class DefaultFrontendGlobalSetting
  extends GlobalSettings
    with FrontendFilters
    with GraphiteConfig
    with RemovingOfTrailingSlashes
    with Routing.BlockingOfPaths
    with ErrorAuditingSettings {

  lazy val appName = Play.current.configuration.getString("appName").getOrElse("APP NAME NOT SET")
  lazy val enableSecurityHeaderFilter = Play.current.configuration.getBoolean("security.headers.filter.enabled").getOrElse(true)


  override lazy val deviceIdFilter = DeviceIdCookieFilter(appName, auditConnector)

  override def onStart(app: Application) {
    Logger.info(s"Starting frontend : $appName : in mode : ${app.mode}")
    super.onStart(app)
  }

  def filters = if (enableSecurityHeaderFilter) Seq(securityFilter) ++ frontendFilters  else frontendFilters

  override def doFilter(a: EssentialAction): EssentialAction =
    Filters(super.doFilter(a), filters: _* )

  override def securityFilter: SecurityHeadersFilter = SecurityHeadersFilterFactory.newInstance

}
