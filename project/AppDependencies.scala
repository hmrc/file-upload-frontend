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
  private val bootstrapPlayVersion = "5.11.0"
  private val playPartialsVersion  = "8.2.0-play-28"
  private val clamAvClientVersion  = "7.0.0" // only built against play-26...
  private val akkaVersion          = "2.6.14"

  val compile = Seq(
    "uk.gov.hmrc"        %% "bootstrap-frontend-play-28" % bootstrapPlayVersion,
    "uk.gov.hmrc"        %% "play-partials"              % playPartialsVersion,
    "uk.gov.hmrc"        %% "clamav-client"              % clamAvClientVersion,
    "org.typelevel"      %% "cats-core"                  % "2.6.1",
    "com.amazonaws"      %  "aws-java-sdk"               % "1.11.97",
    "com.lightbend.akka" %% "akka-stream-alpakka-file"   % "3.0.3",
    "com.typesafe.play"  %% "play-json-joda"             % "2.6.14",

    // ensure all akka versions are the same
    "com.typesafe.akka"  %% "akka-actor-typed"           % akkaVersion,
    "com.typesafe.akka"  %% "akka-serialization-jackson" % akkaVersion,
    "com.typesafe.akka"  %% "akka-slf4j"                 % akkaVersion
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"      % bootstrapPlayVersion % "test,it",
    "org.scalamock"          %% "scalamock-scalatest-support" % "3.6.0"              % "test,it",
    "org.mockito"            %% "mockito-scala"               % "1.10.0"             % "test,it",
    "com.vladsch.flexmark"   %  "flexmark-all"                % "0.35.10"            % "test,it",
    "org.jsoup"              %  "jsoup"                       % "1.11.3"             % "test,it",
    "com.typesafe.akka"      %% "akka-testkit"                % akkaVersion          % "test,it",
    "io.findify"             %% "s3mock"                      % "0.2.6"              % "it",
    // ensure all akka versions are the same
    "com.typesafe.akka"      %% "akka-http"                   % "10.1.13"            % "it"
  )

  val dependencies = compile ++ test
}
