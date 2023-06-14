/*
 * Copyright 2023 HM Revenue & Customs
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

import akka.actor.ActorSystem
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.Logger
import play.api.http.HttpErrorHandler
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, EssentialAction, RequestHeader}
import play.api.mvc.Results._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.utils.ErrorResponse
import scala.concurrent.Future

class RedirectionFeatureSpec
  extends AnyWordSpecLike
     with Matchers
     with OptionValues
     with ScalaFutures
     with TestApplicationComponents {

  private val allowedHosts = Seq[String]("*.gov.uk","gov.uk","localhost")

  private val jsonBody = Json.parse("""{ "field": "value" }""")
  private val request = FakeRequest(POST, "/").withJsonBody(jsonBody)

  val Action =
    stubControllerComponents(bodyParser = stubBodyParser(AnyContent(jsonBody))) // we can't get the body from the request?
      .actionBuilder

  val redirectionFeature = new RedirectionFeature(allowedHosts, null)
  val redirectionWithExceptions =
    new RedirectionFeature(
      allowedHosts,
      new HttpErrorHandler() {
        private val logger = Logger(getClass)

        implicit val erFormats = Json.format[ErrorResponse]

        override def onClientError(request: RequestHeader, statusCode: Int, message: String) = ???

        override def onServerError(request: RequestHeader, ex: Throwable) = {
          logger.error(ex.getMessage, ex)
          Future.successful {
            val (code, message) = ex match {
              case e: Throwable => (INTERNAL_SERVER_ERROR, e.getMessage)
            }
            new Status(code)(Json.toJson(ErrorResponse(code, message)))
          }
        }
      }
    )

  val okAction: EssentialAction = Action { request =>
    val value = (request.body.asJson.get \ "field").as[String]
    Ok(value)
  }

  import redirectionFeature.redirect
  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val actorSystem = ActorSystem()

  "Redirection feature" should {
    "be backward compatible" in {
      val redirectA = redirect(None, None)(okAction)

      val result = call(redirectA, request)

      status(result) shouldEqual OK
      contentAsString(result) shouldEqual "value"
    }

    "fail on incorrect redirect URL" in {
      val redirectA = redirect(None, Some("asdf//:asdf.asdf.pl"))(okAction)

      val result = call(redirectA, request)

      status(result) shouldEqual BAD_REQUEST
    }

    "fail on a URL pretending to be a gov.uk site" in {
      // gov.uk is a .uk tld rather than a restricted .gov.uk one
      // its possible to register a .uk domian ending in gov e.g. fakegov.uk
      val redirectA = redirect(None, Some("https//fakegov.uk/phishing-site"))(okAction)

      val result = call(redirectA, request)

      status(result) shouldEqual BAD_REQUEST
    }

    val OK_URL_ALLOWED = "https://gov.uk"
    val OK_URL_ALLOWED_EXTENDED = "https://service.gov.uk"
    val OK_URL_NOT_ALLOWED = "https://www.o2.pl"

    "redirect on success" in {
      val redirectA = redirect(Some(OK_URL_ALLOWED), None)(okAction)
      val result = call(redirectA, request)

      status(result) shouldEqual MOVED_PERMANENTLY
      header(LOCATION, result).value shouldEqual OK_URL_ALLOWED
    }

    "redirect with right domain base" in {
      val redirectA = redirect(Some(OK_URL_ALLOWED_EXTENDED), None)(okAction)
      val result = call(redirectA, request)

      status(result) shouldEqual MOVED_PERMANENTLY
    }

    "redirect on failure with simple msg error" in {
      val errorMsg = "simple error"
      val badAction: EssentialAction = Action ( _ => NotFound(errorMsg) )
      val redirectA = redirect(None, Some(OK_URL_ALLOWED))(badAction)
      val result = call(redirectA, request)

      status(result) shouldEqual MOVED_PERMANENTLY

      header(LOCATION, result).value shouldEqual (OK_URL_ALLOWED + s"?errorCode=404&reason=" + errorMsg)
    }

    "redirect on failure with exception thrown" in {
      val errorMsg = "Anything can be thrown"
      val badAction: EssentialAction = Action ( _ => throw new RuntimeException("Anything can be thrown"))
      val redirectA = redirectionWithExceptions.redirect(None, Some(OK_URL_ALLOWED))(badAction)
      val result = call(redirectA, request)

      status(result) shouldEqual MOVED_PERMANENTLY

      val location = header(LOCATION, result).value
      (location.length <= 2000) shouldBe true
      location.contains(OK_URL_ALLOWED + s"?errorCode=500&reason=") shouldBe true
      location.contains(errorMsg) shouldBe true
    }

    "redirect on failure with complex msg error" in {
      val errorMsg = """{"error":{"msg":"Request must have exactly 1 file attached"}}"""
      val badAction: EssentialAction = Action (_ => NotFound(errorMsg))
      val redirectA = redirect(None, Some(OK_URL_ALLOWED))(badAction)
      val result = call(redirectA, request)

      status(result) shouldEqual MOVED_PERMANENTLY

      header(LOCATION, result).value shouldEqual (OK_URL_ALLOWED + s"?errorCode=404&reason=" + errorMsg)
    }

    "block not allowed domains" in {
      val redirectA = redirect(None, Some(OK_URL_NOT_ALLOWED))(okAction)

      val result = call(redirectA, request)

      status(result) shouldEqual BAD_REQUEST
    }

    "allow http for localhost if set" in {
      val redirectA = redirect(None, Some("http://localhost"))(okAction)

      val result = call(redirectA, request)

      status(result) shouldEqual OK
    }
  }
}
