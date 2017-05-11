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

      val wrappedAction = withValidEnvelope(_ => Future(Xor.right(envelopeOpen)))(testEnvelopeId)(defaultMaxFileSize => defaultContentType => expectedAction)
      val result = wrappedAction(testRequest).run // this for some reason causes exceptions when running with testOnly

      status(result) shouldBe 200
    }
  }

  "When an envelope is OPEN and has no constraints" should {
    "be possible to execute an Action" in {
      val expectedAction = Action { req => Ok }

      val envelopeOpen = Json.parse("""{ "status" : "OPEN" }""")

      val wrappedAction = withValidEnvelope(_ =>
        Future(Xor.right(envelopeOpen)))(testEnvelopeId)(defaultMaxFileSize => defaultContentType => expectedAction)
      val result = wrappedAction(testRequest).run

      status(result) shouldBe 200
    }
  }

  "When envelope is not OPEN function withExistingEnvelope" should {
    "prevent both action's body and the body parser from running and return 423 Locked" in {
      val statusClosed = "CLOSED"

      val envelopeClosed = Json.parse("""{"status" : "CLOSED", "constraints": {} }""")

      val wrappedAction = withValidEnvelope(_ =>
        Future(Xor.right(envelopeClosed)))(testEnvelopeId)(defaultMaxFileSize => defaultContentType => actionThatShouldNotExecute)

      val result = wrappedAction(testRequest).run

      status(result) shouldBe 423
      contentAsString(result) shouldBe s"""{"message":"Unable to upload to envelope: $testEnvelopeId with status: $statusClosed"}"""
    }
  }

  "When envelope does not exist function withExistingEnvelope" should {
    "prevent both action's body and the body parser from running and return 404 NotFound" in {
      val envNotFound = (envId: EnvelopeId) => Future(Xor.left(EnvelopeDetailNotFoundError(envId)))

      val wrappedAction = withValidEnvelope(envNotFound)(testEnvelopeId)(defaultMaxFileSize => defaultContentType => actionThatShouldNotExecute)
      val result = wrappedAction(testRequest).run

      status(result) shouldBe 404
      contentAsString(result) shouldBe s"""{"message":"Unable to upload to nonexistent envelope: $testEnvelopeId"}"""
    }
  }

  "In case of another error function withExistingEnvelope" should {
    "prevent both action's body and body parser from running and propagate the upstream error" in {
      val errorMsg = "error happened :("
      val errorCheckingStatus = (envId: EnvelopeId) => Future(Xor.left(EnvelopeDetailServiceError(envId, errorMsg)))

      val wrappedAction = withValidEnvelope(errorCheckingStatus)(testEnvelopeId)(defaultMaxFileSize => defaultContentType => actionThatShouldNotExecute)
      val result = wrappedAction(testRequest).run

      status(result) shouldBe 500
      contentType(result).get shouldBe MimeTypes.JSON
      contentAsString(result) should include(errorMsg)
    }
  }

  "When returned envelope data has file constraint: 2KB " should {
    "set the as upload size limit to 2KB" in {
      val envelopeJson = Json.parse("""{"status" : "OPEN", "constraints": { "maxSizePerItem" : "2KB" } }""")

      val expectedSetSize = 2 * 1024
      getMaxFileSizeFromEnvelope(envelopeJson) shouldBe expectedSetSize
    }
  }

  "When returned envelope data has file constraint: 10KB " should {
    "set the as upload size limit to 10KB" in {
      val fileLimit10KB = Json.parse("""{"status" : "OPEN", "constraints": { "maxSizePerItem" : "10KB" } }""")

      val expectedSetSize = 10 * 1024

      getMaxFileSizeFromEnvelope(fileLimit10KB) shouldBe expectedSetSize
    }
  }

  "When returned envelope data has file constraint: 100KB " should {
    "set the as upload size limit to 100KB" in {
      val fileLimit100KB = Json.parse("""{"status" : "OPEN", "constraints": { "maxSizePerItem" : "100KB" } }""")

      val expectedSetSize = 100 * 1024

      getMaxFileSizeFromEnvelope(fileLimit100KB) shouldBe expectedSetSize
    }
  }

  "When returned envelope data has file constraint: 1MB " should {
    "set the as upload size limit to 1MB" in {
      val envelopeJson = Json.parse("""{"status" : "OPEN", "constraints": { "maxSizePerItem" : "1MB" } }""")

      val expectedSetSize = 1 * 1024 * 1024
      getMaxFileSizeFromEnvelope(envelopeJson) shouldBe expectedSetSize
    }
  }

  "When returned envelope data has file constraint: 10MB " should {
    "set the as upload size limit to 10MB" in {
      val fileLimit10MB = Json.parse("""{"status" : "OPEN", "constraints": { "maxSizePerItem" : "10MB" } }""")

      val expectedSetSize = 10 * 1024 * 1024

      getMaxFileSizeFromEnvelope(fileLimit10MB) shouldBe expectedSetSize
    }
  }

  "When returned envelope data has file constraint: 100MB " should {
    "set the as upload size limit to 100MB" in {
      val fileLimit100MB = Json.parse("""{"status" : "OPEN", "constraints": { "maxSizePerItem" : "100MB" } }""")

      val expectedSetSize = 100 * 1024 * 1024

      getMaxFileSizeFromEnvelope(fileLimit100MB) shouldBe expectedSetSize
    }
  }

  "When returned envelope data has empty constraints field " should {
    "set the as upload size limit and content type to default" in {
      val emptyConstraintJson = Json.parse("""{"status" : "OPEN", "constraints": { } }""")

      getMaxFileSizeFromEnvelope(emptyConstraintJson) shouldBe defaultFileSize
      getContentTypeFromEnvelope(emptyConstraintJson) shouldBe defaultContentTypes
    }
  }

  "When returned envelope data has no constraints field " should {
    "set the as upload size limit to default" in {
      val noConstraintJson = Json.parse("""{"status" : "OPEN" }""")

      getMaxFileSizeFromEnvelope(noConstraintJson) shouldBe defaultFileSize
    }
  }

  "When envelope data has specified contentType field in constraints" should {
    "return as List of Content Types" in {
      val contentTypeConstraintJson = Json.parse("""{"status": "OPEN", "constraints": {"contentTypes": ["application/pdf","image/jpeg"]}}""")

      val expectedList = List("application/pdf","image/jpeg")

      getContentTypeFromEnvelope(contentTypeConstraintJson) shouldBe expectedList
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
