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
import com.amazonaws.services.s3.transfer.model.UploadResult
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.{Action, EssentialAction, MultipartFormData, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.fileupload.DomainFixtures._
import uk.gov.hmrc.fileupload.RestFixtures._
import uk.gov.hmrc.fileupload._
import uk.gov.hmrc.fileupload.controllers.EnvelopeChecker._
import uk.gov.hmrc.fileupload.notifier.CommandHandler
import uk.gov.hmrc.fileupload.notifier.NotifierService.NotifySuccess
import uk.gov.hmrc.fileupload.s3.InMemoryMultipartFileHandler
import uk.gov.hmrc.fileupload.s3.S3Service.UploadToQuarantine
import uk.gov.hmrc.play.test.UnitSpec
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.test.{FakeApplication, FakeRequest}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

class RedirectFeatureSpec extends UnitSpec with ScalaFutures with TestApplicationComponents {

  import uk.gov.hmrc.fileupload.ImplicitsSupport.StreamImplicits.materializer
  import scala.concurrent.ExecutionContext.Implicits.global

  val allowedHosts = Seq[String]("gov.uk")
  val redirectionFeature = new RedirectionFeature(allowedHosts)
  import redirectionFeature.redirect

  "Redirection feature" should {
    val okAction: EssentialAction = Action { request =>
      val value = (request.body.asJson.get \ "field").as[String]
      Ok(value)
    }
    val request = FakeRequest(POST, "/").withJsonBody(Json.parse("""{ "field": "value" }"""))

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
    val OK_URL_NOT_ALLOWED = "https://www.o2.pl"

    "redirect on success" in {
      val redirectA = redirect(Some(OK_URL_ALLOWED), None)(okAction)
      val resultF = call(redirectA, request)


      status(resultF) shouldEqual MOVED_PERMANENTLY
      getResultLocation(resultF) shouldEqual OK_URL_ALLOWED
    }

    "redirect on failure with simple msg error" in {
      val errorMsg = "simple error"
      val badAction: EssentialAction = Action { request =>
        NotFound(errorMsg)
      }
      val redirectA = redirect(None, Some(OK_URL_ALLOWED))(badAction)
      val resultF = call(redirectA, request)


      status(resultF) shouldEqual MOVED_PERMANENTLY

      getResultLocation(resultF) shouldEqual (OK_URL_ALLOWED + s"?errorCode:404&reason=" + errorMsg)
    }

    "redirect on failure with complex msg error" in {
      val errorMsg = """{"error":{"msg":"Request must have exactly 1 file attached"}}"""
      val badAction: EssentialAction = Action { request =>
        NotFound(errorMsg)
      }
      val redirectA = redirect(None, Some(OK_URL_ALLOWED))(badAction)
      val resultF = call(redirectA, request)


      status(resultF) shouldEqual MOVED_PERMANENTLY

      getResultLocation(resultF) shouldEqual (OK_URL_ALLOWED + s"?errorCode:404&reason=" + errorMsg)
    }

    "block not allowed domains" in {
      val redirectA = redirect(None, Some(OK_URL_NOT_ALLOWED))(okAction)

      val result = call(redirectA, request)

      status(result) shouldEqual BAD_REQUEST
    }
  }
  def getResultLocation(resultF: Future[Result] )(implicit timeout: Duration): String =
    Await.result(resultF, timeout).header.headers("location")
}