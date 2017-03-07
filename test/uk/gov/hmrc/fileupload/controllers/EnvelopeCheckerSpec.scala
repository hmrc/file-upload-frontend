/*
 * Copyright 2017 HM Revenue & Customs
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

import cats.data.Xor
import play.api.http.MimeTypes
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc.{Action, BodyParser}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.mvc.BodyParser.AnyContent
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.controllers.EnvelopeChecker._
import uk.gov.hmrc.fileupload.transfer.TransferService.{EnvelopeDetailNotFoundError, EnvelopeDetailServiceError}
import uk.gov.hmrc.fileupload.utils.StreamsConverter
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EnvelopeCheckerSpec extends UnitSpec {

  import uk.gov.hmrc.fileupload.ImplicitsSupport.StreamImplicits.materializer

  val testRequest = FakeRequest()

  val testEnvelopeId = EnvelopeId()

  val defaultFileSize = 10 * 1024 * 1024

  "When an envelope is OPEN it" should {
    "be possible to execute an Action" in {
      val expectedAction = Action { req => Ok }

      val envelopeOpen = Json.parse("""{ "status" : "OPEN", "constraints" : {} }""")

      val wrappedAction = withValidEnvelope(_ => Future(Xor.right(envelopeOpen)))(testEnvelopeId)(defaultMaxFileSize => expectedAction)
      val result = wrappedAction(testRequest).run // this for some reason causes exceptions when running with testOnly

      status(result) shouldBe 200
    }
  }

  "When envelope is not OPEN function withExistingEnvelope" should {
    "prevent both action's body and the body parser from running and return 423 Locked" in {
      val statusClosed = "CLOSED"

      val envelopeClosed = Json.parse("""{"status" : "CLOSED", "constraints": {} }""")

      val wrappedAction = withValidEnvelope(_ => Future(Xor.right(envelopeClosed)))(testEnvelopeId)(defaultMaxFileSize => actionThatShouldNotExecute)
      val result = wrappedAction(testRequest).run

      status(result) shouldBe 423
      contentAsString(result) shouldBe s"""{"message":"Unable to upload to envelope: $testEnvelopeId with status: $statusClosed"}"""
    }
  }

  "When envelope does not exist function withExistingEnvelope" should {
    "prevent both action's body and the body parser from running and return 404 NotFound" in {
      val envNotFound = (envId: EnvelopeId) => Future(Xor.left(EnvelopeDetailNotFoundError(envId)))

      val wrappedAction = withValidEnvelope(envNotFound)(testEnvelopeId)(defaultMaxFileSize => actionThatShouldNotExecute)
      val result = wrappedAction(testRequest).run

      status(result) shouldBe 404
      contentAsString(result) shouldBe s"""{"message":"Unable to upload to nonexistent envelope: $testEnvelopeId"}"""
    }
  }

  "In case of another error function withExistingEnvelope" should {
    "prevent both action's body and body parser from running and propagate the upstream error" in {
      val errorMsg = "error happened :("
      val errorCheckingStatus = (envId: EnvelopeId) => Future(Xor.left(EnvelopeDetailServiceError(envId, errorMsg)))

      val wrappedAction = withValidEnvelope(errorCheckingStatus)(testEnvelopeId)(defaultMaxFileSize => actionThatShouldNotExecute)
      val result = wrappedAction(testRequest).run

      status(result) shouldBe 500
      contentType(result).get shouldBe MimeTypes.JSON
      contentAsString(result) should include(errorMsg)
    }
  }

  "When returned envelope data has file constraint: 2KB " should {
    "set the as upload size limit to 2KB" in {
      val envelope2MB = Json.parse("""{"status" : "OPEN", "constraints": { "maxSizePerItem" : "2KB" } }""")

      val expectedSetSize = 2 * 1024

      setMaxFileSize(envelope2MB) shouldBe expectedSetSize
    }
  }

  "When returned envelope data has file constraint: 10KB " should {
    "set the as upload size limit to 10KB" in {
      val envelope10KB = Json.parse("""{"status" : "OPEN", "constraints": { "maxSizePerItem" : "10KB" } }""")

      val expectedSetSize = 10 * 1024

      setMaxFileSize(envelope10KB) shouldBe expectedSetSize
    }
  }

  "When returned envelope data has file constraint: 100KB " should {
    "set the as upload size limit to 100KB" in {
      val envelope2MB = Json.parse("""{"status" : "OPEN", "constraints": { "maxSizePerItem" : "100KB" } }""")

      val expectedSetSize = 100 * 1024

      setMaxFileSize(envelope2MB) shouldBe expectedSetSize
    }
  }

  "When returned envelope data has file constraint: 2MB " should {
    "set the as upload size limit to 2MB" in {
      val envelope2MB = Json.parse("""{"status" : "OPEN", "constraints": { "maxSizePerItem" : "2MB" } }""")

      val expectedSetSize = 2 * 1024 * 1024

      setMaxFileSize(envelope2MB) shouldBe expectedSetSize
    }
  }

  "When returned envelope data has file constraint: 10MB " should {
    "set the as upload size limit to 10MB" in {
      val envelope2MB = Json.parse("""{"status" : "OPEN", "constraints": { "maxSizePerItem" : "10MB" } }""")

      val expectedSetSize = 10 * 1024 * 1024

      setMaxFileSize(envelope2MB) shouldBe expectedSetSize
    }
  }

  "When returned envelope data has file constraint: 100MB " should {
    "set the as upload size limit to 10MB" in {
      val envelope2MB = Json.parse("""{"status" : "OPEN", "constraints": { "maxSizePerItem" : "100MB" } }""")

      setMaxFileSize(envelope2MB) shouldBe defaultFileSize
    }
  }


  "When returned envelope data has file constraint: 2GB " should {
    "set the as upload size limit to default: 10MB" in {
      val envelope2MB = Json.parse("""{"status" : "OPEN", "constraints": { "maxSizePerItem" : "2GB" } }""")

      setMaxFileSize(envelope2MB) shouldBe defaultFileSize
    }
  }

  "When returned envelope data has file constraint not defined" should {
    "set the as upload size limit to default: 10MB " in {
      val envelopeNoConstraints = Json.parse("""{"status" : "OPEN", "constraints": { } }""")

      setMaxFileSize(envelopeNoConstraints) shouldBe defaultFileSize
    }
  }

  def actionThatShouldNotExecute = Action(bodyParserThatShouldNotExecute) { req =>
    fail("action executed which we wanted to prevent")
  }

  def bodyParserThatShouldNotExecute: BodyParser[AnyContent] = BodyParser { r =>
    StreamsConverter.iterateeToAccumulator(Iteratee.consume[Array[Byte]]())
      .map { _ =>
        fail("body parser executed which we wanted to prevent")
      }
  }


}
