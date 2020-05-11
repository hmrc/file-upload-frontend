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

import org.scalatest.concurrent.ScalaFutures
import play.api.Logger
import play.api.http.HttpErrorHandler
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc.{Action, EssentialAction, RequestHeader, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.utils.ErrorResponse
import uk.gov.hmrc.play.test.UnitSpec
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class RedirectionFeatureSpec extends UnitSpec with ScalaFutures with TestApplicationComponents {

  private val allowedHosts = Seq[String]("gov.uk","localhost")
  private val request = FakeRequest(POST, "/").withJsonBody(Json.parse("""{ "field": "value" }"""))

  val redirectionFeature = new RedirectionFeature(allowedHosts, null)
  val redirectionWithExceptions = {
    new RedirectionFeature(allowedHosts, new HttpErrorHandler() {
      implicit val erFormats = Json.format[ErrorResponse]

      override def onClientError(request: RequestHeader, statusCode: Int, message: String) = ???

      override def onServerError(request: RequestHeader, ex: Throwable) = {
        Logger.error(ex.getMessage, ex)
        Future.successful {
          val (code, message) = ex match {
            case e: Throwable => (INTERNAL_SERVER_ERROR, e.getMessage)
          }
          new Status(code)(Json.toJson(ErrorResponse(code, message)))
        }
      }
    })
  }

  val okAction: EssentialAction = Action { request =>
    val value = (request.body.asJson.get \ "field").as[String]
    Ok(value)
  }

  import redirectionFeature.redirect
  import uk.gov.hmrc.fileupload.ImplicitsSupport.StreamImplicits.materializer
  import scala.concurrent.ExecutionContext.Implicits.global

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

    val OK_URL_ALLOWED = "https://gov.uk"
    val OK_URL_ALLOWED_EXTENDED = "https://service.gov.uk"
    val OK_URL_NOT_ALLOWED = "https://www.o2.pl"

    "redirect on success" in {
      val redirectA = redirect(Some(OK_URL_ALLOWED), None)(okAction)
      val resultF = call(redirectA, request)

      status(resultF) shouldEqual MOVED_PERMANENTLY
      getResultLocation(resultF) shouldEqual OK_URL_ALLOWED
    }

    "redirect with right domain base" in {
      val redirectA = redirect(Some(OK_URL_ALLOWED_EXTENDED), None)(okAction)
      val resultF = call(redirectA, request)

      status(resultF) shouldEqual MOVED_PERMANENTLY
    }

    "redirect on failure with simple msg error" in {
      val errorMsg = "simple error"
      val badAction: EssentialAction = Action ( _ => NotFound(errorMsg) )
      val redirectA = redirect(None, Some(OK_URL_ALLOWED))(badAction)
      val resultF = call(redirectA, request)

      status(resultF) shouldEqual MOVED_PERMANENTLY

      getResultLocation(resultF) shouldEqual (OK_URL_ALLOWED + s"?errorCode=404&reason=" + errorMsg)
    }

    "redirect on failure with exception thrown" in {
      val errorMsg = "Anything can be thrown"
      val badAction: EssentialAction = Action ( _ => throw new RuntimeException("Anything can be thrown"))
      val redirectA = redirectionWithExceptions.redirect(None, Some(OK_URL_ALLOWED))(badAction)
      val resultF = call(redirectA, request)

      status(resultF) shouldEqual MOVED_PERMANENTLY


      (getResultLocation(resultF).length <= 2000) shouldBe true
      getResultLocation(resultF).contains(OK_URL_ALLOWED + s"?errorCode=500&reason=") shouldBe true
      getResultLocation(resultF).contains(errorMsg) shouldBe true
    }

    "redirect on failure with complex msg error" in {
      val errorMsg = """{"error":{"msg":"Request must have exactly 1 file attached"}}"""
      val badAction: EssentialAction = Action (_ => NotFound(errorMsg))
      val redirectA = redirect(None, Some(OK_URL_ALLOWED))(badAction)
      val resultF = call(redirectA, request)

      status(resultF) shouldEqual MOVED_PERMANENTLY

      getResultLocation(resultF) shouldEqual (OK_URL_ALLOWED + s"?errorCode=404&reason=" + errorMsg)
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
  def getResultLocation(resultF: Future[Result] )(implicit timeout: Duration): String =
    Await.result(resultF, timeout).header.headers("location")
}
