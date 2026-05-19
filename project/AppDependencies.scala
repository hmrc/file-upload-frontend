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
  private val bootstrapPlayVersion = "10.7.0"
  private val connectorsVersion    = "1.0.2"

  val compile = Seq(
    "uk.gov.hmrc"            %% "bootstrap-frontend-play-30" % bootstrapPlayVersion,
    "org.typelevel"          %% "cats-core"                  % "2.13.0",
    "software.amazon.awssdk" %  "s3"                         % "2.44.6",
    "joda-time"              %  "joda-time"                  % "2.14.0",
    "org.apache.pekko"       %% "pekko-connectors-file"      % connectorsVersion,
    "commons-io"             %  "commons-io"                 % "2.19.0"
  )

  val test = Seq(
    "uk.gov.hmrc"        %% "bootstrap-test-play-30"     % bootstrapPlayVersion % Test,
    "org.jsoup"          %  "jsoup"                      % "1.22.2"             % Test,
    "org.apache.pekko"   %% "pekko-testkit"              % pekkoVersion         % Test
  )

  val it = Seq.empty

  val dependencies = compile ++ test

  val overrides = Seq(
    "org.apache.pekko" %% "pekko-actor"                  % pekkoVersion,
    "org.apache.pekko" %% "pekko-actor-typed"            % pekkoVersion,
    "org.apache.pekko" %% "pekko-stream"                 % pekkoVersion,
    "org.apache.pekko" %% "pekko-protobuf-v3"            % pekkoVersion,
    "org.apache.pekko" %% "pekko-slf4j"                  % pekkoVersion,
    "org.apache.pekko" %% "pekko-serialization-jackson"  % pekkoVersion
  )
}
