package uk.gov.hmrc.fileupload.errorHandle

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpecLike}
import play.api.GlobalSettings
import play.api.http.MimeTypes
import play.api.mvc.RequestHeader
import uk.gov.hmrc.fileupload.utils.ShowErrorAsJson
import uk.gov.hmrc.play.http.{BadRequestException, NotFoundException, UnauthorizedException}

class ShowErrorAsJsonSpec extends WordSpecLike with Matchers with ScalaFutures with MockitoSugar {

  val jsh = new GlobalSettings with ShowErrorAsJson

  val requestHeader = mock[RequestHeader]

  "error handling in onError function" should {

    "convert a NotFoundException to NotFound response" in {
      val ex = new NotFoundException("test")
      val resultF = jsh.onError(requestHeader, ex).futureValue
      resultF.header.status shouldBe 404

      val contentType = resultF.header.headers("Content-Type").split(";")
      contentType(0) shouldBe MimeTypes.JSON
    }

    "convert a BadRequestException to NotFound response" in {
      val resultF = jsh.onError(requestHeader, new BadRequestException("bad request")).futureValue
      resultF.header.status shouldBe 400

      val contentType = resultF.header.headers("Content-Type").split(";")
      contentType(0) shouldBe MimeTypes.JSON
    }

    "convert an UnauthorizedException to Unauthorized response" in {
      val resultF = jsh.onError(requestHeader, new UnauthorizedException("unauthorized")).futureValue
      resultF.header.status shouldBe 401

      val contentType = resultF.header.headers("Content-Type").split(";")
      contentType(0) shouldBe MimeTypes.JSON
    }

    "convert an Exception to InternalServerError" in {
      val resultF = jsh.onError(requestHeader, new Exception("any application exception")).futureValue
      resultF.header.status shouldBe 500

      val contentType = resultF.header.headers("Content-Type").split(";")
      contentType(0) shouldBe MimeTypes.JSON
    }

  }

}
