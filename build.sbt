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

import play.routes.compiler.InjectedRoutesGenerator
import play.sbt.PlayImport.PlayKeys
import play.sbt.routes.RoutesKeys.routesGenerator
import sbt.Tests.{Group, SubProcess}
import scoverage.ScoverageKeys
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion
import uk.gov.hmrc.DefaultBuildSettings

val appName = "file-upload-frontend"

lazy val scoverageSettings =
  Seq(
    ScoverageKeys.coverageExcludedPackages := List("<empty>", "Reverse.*", ".*AuthService.*", "models/.data/..*", "view.*").mkString(";"),
    ScoverageKeys.coverageExcludedFiles := List(".*/frontendGlobal.*", ".*/frontendAppConfig.*", ".*/frontendWiring.*",
      ".*/views/.*_template.*", ".*/govuk_wrapper.*", ".*/routes_routing.*", ".*/BuildInfo.*").mkString(";"),
    // Minimum is deliberately low to avoid failures initially - please increase as we add more coverage
    ScoverageKeys.coverageMinimum := 25,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true,
    parallelExecution in Test := false
  )

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(majorVersion := 1)
  .settings(PlayKeys.playDefaultPort := 8899)
  .settings(scoverageSettings: _*)
  .settings(SbtDistributablesPlugin.publishingSettings: _*)
  .settings(
    scalaVersion := "2.12.12",
    libraryDependencies ++= AppDependencies.dependencies,
    parallelExecution in Test := false,
    retrieveManaged := true,
    evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
    routesGenerator := InjectedRoutesGenerator
  )
  .configs(IntegrationTest)
  .settings(
    DefaultBuildSettings.integrationTestSettings,
    // since it depends on test, we must explicitly add it, and filter out the test specs.
    // this requires explicitly adding the test report option since we have replaced the testOptions.
    unmanagedSourceDirectories in IntegrationTest := (baseDirectory in IntegrationTest)(base => Seq(base / "it", base / "test")).value,
    testOptions in IntegrationTest := Seq(Tests.Filter(_.endsWith("ISpec"))),
    DefaultBuildSettings.addTestReportOption(IntegrationTest, "int-test-reports")
  )
  .settings(
    resolvers += Resolver.jcenterRepo // for metrics-play
  )
