package uk.gov.hmrc.fileupload

import org.scalatest.{FeatureSpecLike, GivenWhenThen, Matchers}
import org.scalatest.concurrent.Eventually
import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.DomainFixtures.{anyEnvelopeId, anyFileId}
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions}


class FileUploadRedirectionISpec extends FeatureSpecLike with GivenWhenThen with FileActions with EnvelopeActions with Eventually with Matchers {
  val FAIL_REDIRECT_PARAM_NAME = "redirect-error-url"
  val SUCC_REDIRECT_PARAM_NAME = "redirect-success-url"

  val DEV_YOUR_PAY = "https://www-dev.tax.service.gov.uk/estimate-paye-take-home-pay/your-pay"
  val QA_YOUR_PAY = "https://www-qa.tax.service.gov.uk/estimate-paye-take-home-pay/your-pay"
  val NO_HTTPS = DEV_YOUR_PAY.replaceAll("https", "http")
  val NOT_GOV_DOMAIN = "https://www.playframework.com/documentation/2.5.x/ScalaTestingWithScalaTest"

  feature("Redirect End User when provided with redirect parameters as part of upload file request") {

    val fileId = anyFileId
    val envelopeId = anyEnvelopeId
    scenario("Redirect upon success to valid url - only success url provided") {

      Given("Envelope created with default parameters")
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)

      When("a file is uploaded provided a redirect on success to a https://www-dev.tax.service.gov.uk url")

      val redirectSuccessUrl = DEV_YOUR_PAY
      val queryParam = s"$SUCC_REDIRECT_PARAM_NAME=$redirectSuccessUrl"
      val uploadFileResponse = uploadDummyFileWithoutRedirects(envelopeId, fileId, queryParam)
      Then("upon success the user should be redirected to the url specified in the query parameter")
      uploadFileResponse.status should be(301)
      uploadFileResponse.header("Location").get shouldBe redirectSuccessUrl
    }

    scenario("Redirect upon success to valid url - both success and error urls provided") {

      Given("Envelope created with default parameters")
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)

      When("a file is uploaded provided a redirect on success to a https://www-dev.tax.service.gov.uk url")
      val redirectSuccessUrl = DEV_YOUR_PAY
      val redirectErrorUrl = QA_YOUR_PAY

      val queryParam = s"$SUCC_REDIRECT_PARAM_NAME=$redirectSuccessUrl&$FAIL_REDIRECT_PARAM_NAME=$redirectErrorUrl"
      val uploadFileResponse = uploadDummyFileWithoutRedirects(envelopeId, fileId, queryParam)
      Then("upon success the user should be redirected to the url specified in the query parameter")
      uploadFileResponse.status should be(301)
      uploadFileResponse.header("Location").get shouldBe redirectSuccessUrl
    }

    scenario("Redirect upon error to valid url - both success and error urls provided") {

      Given("An envelope which does not exist")
      val invalidEnvelopeId = EnvelopeId("12345-123124")

      When("a file is uploaded provided a redirect on error to a https://www-dev.tax.service.gov.uk url")
      val redirectSuccessUrl = "https://www-dev.tax.service.gov.uk/estimate-paye-take-home-pay/your-pay"
      val redirectErrorUrl = "https://www-qa.tax.service.gov.uk/estimate-paye-take-home-pay/your-pay"
      val queryParam = s"redirect-success-url=$redirectSuccessUrl&redirect-error-url=$redirectErrorUrl"
      val uploadFileResponse = uploadDummyFileWithoutRedirects(invalidEnvelopeId, fileId, queryParam)

      Then("upon success the user should be redirected to the url specified in the query parameter")
      uploadFileResponse.status should be(301)
      uploadFileResponse.header("Location") shouldBe redirectErrorUrl
    }


    scenario("Redirect upon success to invalid url - not https") {
      Given("Envelope created with default parameters")
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)

      When("a file is uploaded provided a redirect on success to a https://www-dev.tax.service.gov.uk url")
      val redirectSuccessUrl = NO_HTTPS
      val queryParam = s"$SUCC_REDIRECT_PARAM_NAME=$redirectSuccessUrl"

      val uploadFileResponse = uploadDummyFileWithoutRedirects(envelopeId, fileId, queryParam)
      Then("a Bad Request response should be received")
      uploadFileResponse.status should be(400)

      And("The message should indicate URL is invalid")
      val expectedMessage = "URL is invalid"
      val parsedBody = Json.parse(uploadFileResponse.body)
      val message = (parsedBody \ "message").as[String]
      message shouldBe expectedMessage
    }

    scenario("Redirect upon error to invalid url - not https") {
      Given("Envelope created with default parameters")
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)

      When("a file is uploaded provided a redirect on success to a https://www-dev.tax.service.gov.uk url")
      val redirectErrorUrl = NO_HTTPS
      val queryParam = s"$FAIL_REDIRECT_PARAM_NAME=$redirectErrorUrl"

      val uploadFileResponse = uploadDummyFileWithoutRedirects(envelopeId, fileId, queryParam)
      Then("a Bad Request response should be received")
      uploadFileResponse.status should be(400)

      And("The message should indicate URL is invalid")
      val expectedMessage = "URL is invalid"
      val parsedBody = Json.parse(uploadFileResponse.body)
      val message = (parsedBody \ "message").as[String]
      message shouldBe expectedMessage
    }


    scenario("Redirect upon success to invalid url - not service.gov.uk") {
      Given("Envelope created with default parameters")
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)

      When("a file is uploaded provided a redirect on success to an https but non gov.uk url")
      val redirectSuccessUrl = NOT_GOV_DOMAIN
      val queryParam = s"$SUCC_REDIRECT_PARAM_NAME=$redirectSuccessUrl"
      val uploadFileResponse = uploadDummyFileWithoutRedirects(envelopeId, fileId, queryParam)

      Then("a Bad Request response should be received")
      uploadFileResponse.status should be(400)

      And("The message should indicate URL is invalid")
      val expectedMessage = "URL is invalid"
      val parsedBody = Json.parse(uploadFileResponse.body)
      val message = (parsedBody \ "message").as[String]
      message shouldBe expectedMessage
    }

    scenario("Redirect upon error to invalid url - not service.gov.uk") {
      Given("Envelope created with default parameters")
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)

      When("a file is uploaded provided a redirect on error to an https but non gov.uk url")
      val redirectErrorUrl = NOT_GOV_DOMAIN
      val queryParam = s"$FAIL_REDIRECT_PARAM_NAME=$redirectErrorUrl"
      val uploadFileResponse = uploadDummyFileWithoutRedirects(envelopeId, fileId, queryParam)

      Then("a Bad Request response should be received")
      uploadFileResponse.status should be(400)

      And("The message should indicate URL is invalid")
      val expectedMessage = "URL is invalid"
      val parsedBody = Json.parse(uploadFileResponse.body)
      val message = (parsedBody \ "message").as[String]
      message shouldBe expectedMessage
    }


  }

}
