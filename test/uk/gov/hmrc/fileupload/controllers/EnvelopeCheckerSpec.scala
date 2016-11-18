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

package uk.gov.hmrc.fileupload.controllers

import uk.gov.hmrc.fileupload.controllers.EnvelopeChecker._
import uk.gov.hmrc.play.test.UnitSpec
import TestFixtures._
import cats.data.Xor
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, EssentialAction}
import play.api.mvc.Results._
import uk.gov.hmrc.play.test.UnitSpec
import play.api.test.Helpers._
import play.api.test.FakeRequest


import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Created by paul on 17/11/16.
  */
class EnvelopeCheckerSpec extends UnitSpec{

  val fakeAction = Action { Ok }
  val request = FakeRequest()
  val testEnvelopeId = testEnvelope

  "When an envelope is OPEN it" should {
    "be possible to execute an Action" in {
      val action = withExistingEnvelope(testEnvelopeId, _ => Future(Xor.right("OPEN")))(fakeAction)
      val result = action(request).run // this for some reason causes exceptions when run a single test with testOnly
      status(result) shouldBe 200
    }
  }

  // TODO: Verify that parser didn't run.

  "When envelope is not OPEN function withExistingEnvelope" should {
    "prevent action from running and return 400 with an explanation" in {
      val action = withExistingEnvelope(testEnvelopeId, _ => Future(Xor.right("CLOSED")))(fakeAction)
      val result = action(request).run
      status(result) shouldBe 400
      contentAsString(result) shouldBe s"""{"message":"Unable to upload to envelope: $testEnvelopeId with status: CLOSED"}"""
    }
  }

  "Envelope does not exist" should {
    "not allow a file to upload to an non-existing envelope" in {

    }
  }

  "A server error" should {
    "prevent uploading of a file" in{

    }
  }

}
