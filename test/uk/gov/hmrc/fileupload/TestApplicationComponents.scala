/*
 * Copyright 2021 HM Revenue & Customs
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

import org.scalatest.{BeforeAndAfterAll, Suite}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

trait TestApplicationComponents
  extends AnyWordSpecLike
     with Matchers
     with GuiceOneServerPerSuite
     with BeforeAndAfterAll {
  this: Suite =>

  // creates a new application and sets the components
  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .configure("metrics.jvm" -> false)
      .build()
}
