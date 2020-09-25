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

private object AppDependencies {

  import play.core.PlayVersion

  private val frontendBootstrapVersion = "12.4.0"
  private val playPartialsVersion      = "6.5.0"
  private val authClient               = "2.17.0-play-26"
  private val playConfigVersion        = "7.3.0"
  private val hmrcTestVersion          = "3.3.0"
  private val clamAvClientVersion      = "6.9.0"

  val compile = Seq(
    "uk.gov.hmrc"        %% "frontend-bootstrap"       % frontendBootstrapVersion,
    "uk.gov.hmrc"        %% "play-partials"            % playPartialsVersion,
    "uk.gov.hmrc"        %% "auth-client"              % authClient,
    "uk.gov.hmrc"        %% "clamav-client"            % clamAvClientVersion,
    "org.typelevel"      %% "cats"                     % "0.6.0",
    "com.amazonaws"      %  "aws-java-sdk"             % "1.11.97",
    "com.lightbend.akka" %% "akka-stream-alpakka-file" % "2.0.1",
    "com.typesafe.play"  %% "play-iteratees-reactive-streams" % "2.6.1",
    "com.typesafe.play"  %% "play-json-joda"           % "2.6.14",

    // ensure all akka versions are the same
    "com.typesafe.akka"  %% "akka-slf4j"               % "2.5.31"
  )

  val test = Seq(
        "uk.gov.hmrc"            %% "hmrctest"                    % hmrcTestVersion     % "test,it",
        "org.scalatest"          %% "scalatest"                   % "3.0.5"             % "test,it",
        "org.scalamock"          %% "scalamock-scalatest-support" % "3.6.0"             % "test,it",
        "com.github.tomakehurst" %  "wiremock"                    % "1.58"              % "test,it",
        "org.scalatestplus.play" %% "scalatestplus-play"          % "2.0.1"             % "test,it",
        "org.pegdown"            %  "pegdown"                     % "1.6.0"             % "test,it",
        "org.jsoup"              %  "jsoup"                       % "1.11.3"            % "test,it",
        "com.typesafe.play"      %% "play-test"                   % PlayVersion.current % "test,it",
        "com.typesafe.akka"      %% "akka-testkit"                % "2.3.2"             % "test,it",
        "io.findify"             %% "s3mock"                      % "0.2.5"             % "it"
      )

  val dependencies = compile ++ test
}
