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

package uk.gov.hmrc.fileupload.errorHandle

import org.scalatest.concurrent.ScalaFutures
import play.api.test.FakeRequest
import uk.gov.hmrc.fileupload.TestApplicationComponents
import uk.gov.hmrc.fileupload.utils.ShowErrorAsJson
import uk.gov.hmrc.play.http.BadRequestException
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext

class ShowErrorAsJsonSpec extends UnitSpec with ScalaFutures with TestApplicationComponents {

  implicit val ec = ExecutionContext.global
  val errorHandler = new ShowErrorAsJson(components.environment, components.configuration)

  "Error Handler For the Controllers" should {

    "convert a BadRequestException to NotFound response" in {
      val result = errorHandler.onServerError(FakeRequest(), new BadRequestException("40x Bad Request"))
      result.map(res => res.header.status should be(404))
    }

    "500 Internal Server Error is handled" in {

      val result = errorHandler.onServerError(FakeRequest(), new RuntimeException("Unexpected Error"))
      result.map(res => {
        res.header.status should be(500)
        res.body.contentType should be(Some("application/json"))
      })
    }


  }

}
