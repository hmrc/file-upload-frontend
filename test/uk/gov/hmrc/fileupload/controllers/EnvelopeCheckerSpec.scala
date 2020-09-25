/*
 * Copyright 2020 HM Revenue & Customs
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
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc.Results._
import play.api.mvc.{Action, BodyParser}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.mvc.BodyParser.AnyContent
import uk.gov.hmrc.fileupload.EnvelopeId
import uk.gov.hmrc.fileupload.controllers.EnvelopeChecker._
import uk.gov.hmrc.fileupload.transfer.TransferService.{EnvelopeDetailNotFoundError, EnvelopeDetailServiceError}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EnvelopeCheckerSpec extends UnitSpec {

  import uk.gov.hmrc.fileupload.ImplicitsSupport.StreamImplicits.materializer

  val testRequest = FakeRequest()

  val testEnvelopeId = EnvelopeId()

  val defaultFileSize = 10 * 1024 * 1024

  def envelopeMaxSizePerItemJson(size:String) = Json.parse(
    s"""{"status" : "OPEN",
            "constraints": {
            "maxItems":100,
            "maxSize": "25MB",
            "maxSizePerItem" : "$size",
            "contentTypes": ["application/pdf","image/jpeg","application/xml","text/xml"]
            } }""".stripMargin)

  def envelopeContentTypesJson(contentTypes:String) = Json.parse(
    s"""{"status" : "OPEN",
            "constraints": {
            "maxItems":100,
            "maxSize": "25MB",
            "maxSizePerItem" : "10MB",
            "contentTypes": [$contentTypes]
            } }""".stripMargin)

  def envelopeAllowZeroLengthFiles(allowZeroLengthFiles: Option[Boolean]) = Json.parse(
    s"""{"status" : "OPEN",
            "constraints": {
            ${allowZeroLengthFiles.map(value => s""""allowZeroLengthFiles": $value,""").getOrElse("")}
            "maxItems":100,
            "maxSize": "25MB",
            "maxSizePerItem" : "10MB",
       |    "contentTypes": ["application/pdf","image/jpeg","application/xml","text/xml"]
            } }""".stripMargin)

  "When an envelope is OPEN it" should {
    "be possible to execute an Action" in {
      val expectedAction = Action { _ => Ok }

      val envelopeOpen = Json.parse("""{ "status" : "OPEN" }""")

      val wrappedAction = withValidEnvelope(_ => Future(Xor.right(envelopeOpen)))(testEnvelopeId)(_ => expectedAction)
      val result = wrappedAction(testRequest).run // this for some reason causes exceptions when running with testOnly

      status(result) shouldBe 200
    }
  }

  "When an envelope is OPEN and has no constraints" should {
    "be possible to execute an Action" in {
      val expectedAction = Action { _ => Ok }

      val envelopeOpen = Json.parse("""{ "status" : "OPEN" }""")

      val wrappedAction = withValidEnvelope(_ =>
        Future(Xor.right(envelopeOpen)))(testEnvelopeId)(_ => expectedAction)
      val result = wrappedAction(testRequest).run

      status(result) shouldBe 200
    }
  }

  "When envelope is not OPEN function withExistingEnvelope" should {
    "prevent both action's body and the body parser from running and return 423 Locked" in {
      val statusClosed = "CLOSED"

      val envelopeClosed = Json.parse("""{"status" : "CLOSED" }""")

      val wrappedAction = withValidEnvelope(_ =>
        Future(Xor.right(envelopeClosed)))(testEnvelopeId)(_ => actionThatShouldNotExecute)

      val result = wrappedAction(testRequest).run

      status(result) shouldBe 423
      contentAsString(result) shouldBe s"""{"message":"Unable to upload to envelope: $testEnvelopeId with status: $statusClosed"}"""
    }
  }

  "When envelope does not exist function withExistingEnvelope" should {
    "prevent both action's body and the body parser from running and return 404 NotFound" in {
      val envNotFound = (envId: EnvelopeId) => Future(Xor.left(EnvelopeDetailNotFoundError(envId)))

      val wrappedAction = withValidEnvelope(envNotFound)(testEnvelopeId)(_ => actionThatShouldNotExecute)
      val result = wrappedAction(testRequest).run

      status(result) shouldBe 404
      contentAsString(result) shouldBe s"""{"message":"Unable to upload to nonexistent envelope: $testEnvelopeId"}"""
    }
  }

  "In case of another error function withExistingEnvelope" should {
    "prevent both action's body and body parser from running and propagate the upstream error" in {
      val errorMsg = "error happened :("
      val errorCheckingStatus = (envId: EnvelopeId) => Future(Xor.left(EnvelopeDetailServiceError(envId, errorMsg)))

      val wrappedAction = withValidEnvelope(errorCheckingStatus)(testEnvelopeId)(_ => actionThatShouldNotExecute)
      val result = wrappedAction(testRequest).run

      status(result) shouldBe 500
      contentType(result).get shouldBe MimeTypes.JSON
      contentAsString(result) should include(errorMsg)
    }
  }

  "When returned envelope data has file constraint: 2KB " should {
    "set the as upload size limit to 2KB" in {
      val expectedSetSize = 2 * 1024
      val constraints2KB = extractEnvelopeDetails(envelopeMaxSizePerItemJson("2KB")).constraints
      getMaxFileSizeFromEnvelope(constraints2KB) shouldBe expectedSetSize
    }
  }

  "When returned envelope data has file constraint: 10KB " should {
    "set the as upload size limit to 10KB" in {
      val expectedSetSize = 10 * 1024
      val constraints10KB = extractEnvelopeDetails(envelopeMaxSizePerItemJson("10KB")).constraints
      getMaxFileSizeFromEnvelope(constraints10KB) shouldBe expectedSetSize
    }
  }

  "When returned envelope data has file constraint: 100KB " should {
    "set the as upload size limit to 100KB" in {
      val expectedSetSize = 100 * 1024
      val constraints100KB = extractEnvelopeDetails(envelopeMaxSizePerItemJson("100KB")).constraints
      getMaxFileSizeFromEnvelope(constraints100KB) shouldBe expectedSetSize
    }
  }

  "When returned envelope data has file constraint: 1MB " should {
    "set the as upload size limit to 1MB" in {
      val expectedSetSize = 1 * 1024 * 1024
      val constraints1MB = extractEnvelopeDetails(envelopeMaxSizePerItemJson("1MB")).constraints
      getMaxFileSizeFromEnvelope(constraints1MB) shouldBe expectedSetSize
    }
  }

  "When returned envelope data has file constraint: 10MB " should {
    "set the as upload size limit to 10MB" in {
      val expectedSetSize = 10 * 1024 * 1024
      val constraints10MB = extractEnvelopeDetails(envelopeMaxSizePerItemJson("10MB")).constraints
      getMaxFileSizeFromEnvelope(constraints10MB) shouldBe expectedSetSize
    }
  }

  "When returned envelope data has file constraint: 100MB " should {
    "set the as upload size limit to 100MB" in {
      val expectedSetSize = 100 * 1024 * 1024
      val constraints100MB = extractEnvelopeDetails(envelopeMaxSizePerItemJson("100MB")).constraints
      getMaxFileSizeFromEnvelope(constraints100MB) shouldBe expectedSetSize
    }
  }

  "When returned envelope data has no constraints field " should {
    "set the as upload size limit" in {
      val emptyConstraintJson = Json.parse("""{"status" : "OPEN" }""")
      val constraintsEmpty = extractEnvelopeDetails(emptyConstraintJson).constraints
      getMaxFileSizeFromEnvelope(constraintsEmpty) shouldBe defaultFileSize
    }
  }

  "When returned envelope data does not have allowZeroLengthFiles" should {
    "allowZeroLengthFiles should be undefined in constraints" in {
      val constraints = extractEnvelopeDetails(envelopeAllowZeroLengthFiles(None)).constraints
      constraints.flatMap(_.allowZeroLengthFiles) should not be 'defined
    }
  }

  "When returned envelope data has allowZeroLengthFiles set to true" should {
    "allowZeroLengthFiles should be defined as true in constraints" in {
      val constraints = extractEnvelopeDetails(envelopeAllowZeroLengthFiles(Some(true))).constraints
      constraints.flatMap(_.allowZeroLengthFiles) shouldBe Some(true)
    }
  }

  "When returned envelope data has allowZeroLengthFiles set to false" should {
    "allowZeroLengthFiles should be defined as false in constraints" in {
      val constraints = extractEnvelopeDetails(envelopeAllowZeroLengthFiles(Some(false))).constraints
      constraints.flatMap(_.allowZeroLengthFiles) shouldBe Some(false)
    }
  }

  def actionThatShouldNotExecute = Action(bodyParserThatShouldNotExecute) { _ =>
    fail("action executed which we wanted to prevent")
  }

  def bodyParserThatShouldNotExecute: BodyParser[AnyContent] = BodyParser { _ =>
    Accumulator.done(
      fail("body parser executed which we wanted to prevent")
    )
  }
}
