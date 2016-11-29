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

package uk.gov.hmrc.fileupload.errorHandle

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import play.api.GlobalSettings
import play.api.http.MimeTypes
import play.api.mvc.RequestHeader
import play.api.test.Helpers.{contentType, _}
import uk.gov.hmrc.fileupload.utils.ShowErrorAsJson
import uk.gov.hmrc.play.http.{BadRequestException, NotFoundException, UnauthorizedException}
import uk.gov.hmrc.play.test.UnitSpec

class ShowErrorAsJsonSpec extends UnitSpec with ScalaFutures with MockitoSugar {

  val jsh = new GlobalSettings with ShowErrorAsJson

  val requestHeader = mock[RequestHeader]

  "error handling in onError function" should {

    "convert a NotFoundException to NotFound response" in {
      val result = jsh.onError(requestHeader, new NotFoundException("test")).futureValue
      result.header.status shouldBe 404
      contentType(result).get shouldBe MimeTypes.JSON
    }

    "convert a BadRequestException to NotFound response" in {
      val result = jsh.onError(requestHeader, new BadRequestException("bad request")).futureValue
      result.header.status shouldBe 400
      contentType(result).get shouldBe MimeTypes.JSON
    }

    "convert an UnauthorizedException to Unauthorized response" in {
      val result = jsh.onError(requestHeader, new UnauthorizedException("unauthorized")).futureValue
      result.header.status shouldBe 401
      contentType(result).get shouldBe MimeTypes.JSON
    }

    "convert an Exception to InternalServerError" in {
      val result = jsh.onError(requestHeader, new Exception("any application exception")).futureValue
      result.header.status shouldBe 500
      contentType(result).get shouldBe MimeTypes.JSON
    }

  }

}
