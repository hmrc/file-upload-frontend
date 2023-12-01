/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.support

import org.scalatest.{BeforeAndAfterEach, Suite}
import org.mockito.MockitoSugar
import uk.gov.hmrc.fileupload.virusscan.AvClient

trait ITTestAppComponentsWithStubbedClamAV
  extends IntegrationTestApplicationComponents
     with BeforeAndAfterEach
     with MockitoSugar {
  this: Suite =>

  lazy val stubbedAvClient: AvClient = mock[AvClient]

  override lazy val disableAvScanning: Boolean = false
  override lazy val numberOfTimeoutAttempts: Int = 3
  override lazy val avClient: Option[AvClient] = Some(stubbedAvClient)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(stubbedAvClient)
  }
}
