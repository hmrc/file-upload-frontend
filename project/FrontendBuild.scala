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

import sbt._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning

object FrontendBuild extends Build with MicroService {

  val appName = "file-upload-frontend"

  override lazy val plugins: Seq[Plugins] = Seq(
    SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin
  )

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {

  import play.sbt.PlayImport._
  import play.core.PlayVersion

  private val playHealthVersion = "2.1.0"
  private val playJsonLoggerVersion = "3.1.0"

  private val frontendBootstrapVersion = "7.23.0"
  private val playPartialsVersion = "5.3.0"
  private val playAuthorisedFrontendVersion = "6.3.0"
  private val playConfigVersion = "4.2.0"
  private val hmrcTestVersion = "2.3.0"
  private val playReactivemongoVersion = "5.2.0"
  private val simpleReactivemongoVersion = "5.2.0"
  private val clamAvClientVersion = "2.4.0"
  private val catsVersion = "0.6.0"
  private val playAuditingVersion = "2.6.0"
  private val playUrlBindingLibVersion = "7.1.0"
  private val awsJavaSdkVersion = "1.11.97"

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "play-reactivemongo" % playReactivemongoVersion,
    "uk.gov.hmrc" %% "simple-reactivemongo" % simpleReactivemongoVersion,
    "uk.gov.hmrc" %% "frontend-bootstrap" % frontendBootstrapVersion,
    "uk.gov.hmrc" %% "play-partials" % playPartialsVersion,
    "uk.gov.hmrc" %% "play-authorised-frontend" % playAuthorisedFrontendVersion,
    "uk.gov.hmrc" %% "play-config" % playConfigVersion,
    "uk.gov.hmrc" %% "logback-json-logger" % playJsonLoggerVersion,
    "uk.gov.hmrc" %% "play-auditing" % playAuditingVersion,
    "uk.gov.hmrc" %% "play-health" % playHealthVersion,
    "uk.gov.hmrc" %% "clamav-client" % clamAvClientVersion,
    "org.typelevel" %% "cats" % catsVersion,
    "uk.gov.hmrc" %% "play-ui" % playUrlBindingLibVersion,
    "com.amazonaws" % "aws-java-sdk" % awsJavaSdkVersion
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test: Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % scope,
        "org.scalatest" %% "scalatest" % "2.2.6" % scope,
        "org.scalamock" %% "scalamock-scalatest-support" % "3.2.2" % scope,
        "com.github.tomakehurst" % "wiremock" % "1.58" % scope,
        "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % scope,
        "org.pegdown" % "pegdown" % "1.6.0" % scope,
        "org.jsoup" % "jsoup" % "1.8.3" % scope,
        "uk.gov.hmrc" %% "reactivemongo-test" % "1.6.0" % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "com.typesafe.akka" %% "akka-testkit" % "2.3.2" % scope
      )
    }.test
  }

  object IntegrationTest {
    def apply() = new TestDependencies {
      override lazy val scope: String = "it"

      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % scope,
        "org.scalatest" %% "scalatest" % "2.2.6" % scope,
        "org.scalamock" %% "scalamock-scalatest-support" % "3.2.2" % scope,
        "com.github.tomakehurst" % "wiremock" % "1.58" % scope,
        "org.pegdown" % "pegdown" % "1.6.0" % scope,
        "org.jsoup" % "jsoup" % "1.8.3" % scope,
        "uk.gov.hmrc" %% "reactivemongo-test" % "1.6.0" % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "com.typesafe.akka" %% "akka-testkit" % "2.3.2" % scope,
        "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % scope,
        "io.findify" %% "s3mock" % "0.1.9" % scope
      )
    }.test
  }

  def apply() = compile ++ Test() ++ IntegrationTest()
}


