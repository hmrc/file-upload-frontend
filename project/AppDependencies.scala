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

import play.core.PlayVersion.pekkoVersion
import sbt._

private object AppDependencies {
  private val bootstrapPlayVersion = "9.1.0"

  val compile = Seq(
    "uk.gov.hmrc"        %% "bootstrap-frontend-play-30" % bootstrapPlayVersion,
    "uk.gov.hmrc"        %% "play-partials-play-30"      % "10.0.0",
    "org.typelevel"      %% "cats-core"                  % "2.10.0",
    "com.amazonaws"      %  "aws-java-sdk-s3"            % "1.12.763",
    "org.apache.pekko"   %% "pekko-connectors-file"      % "1.0.2",
    "commons-io"         %  "commons-io"                 % "2.15.0"
  )

  val test = Seq(
    "uk.gov.hmrc"        %% "bootstrap-test-play-30"     % bootstrapPlayVersion % Test,
    "org.jsoup"          %  "jsoup"                      % "1.17.1"             % Test,
    "org.apache.pekko"   %% "pekko-testkit"              % pekkoVersion         % Test
  )

  val it = Seq(
    "io.findify"         %% "s3mock"                     % "0.2.6"              % Test // https://github.com/findify/s3mock/issues/189
  )

  val itOverrides = Seq(
    "com.typesafe" %% "ssl-config-core" % "0.4.3" % Test // s3Mock depends on this version, but it's evicted
  )

  val dependencies = compile ++ test
}
