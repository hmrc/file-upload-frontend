package uk.gov.hmrc.fileupload

import org.scalatest.{FeatureSpecLike, GivenWhenThen, Matchers}
import org.scalatest.concurrent.Eventually
import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.DomainFixtures.{anyEnvelopeId, anyFileId}
import uk.gov.hmrc.fileupload.support.{EnvelopeActions, FileActions}


class FileUploadRedirectionISpec extends FeatureSpecLike with GivenWhenThen with FileActions with EnvelopeActions with Eventually with Matchers {

  feature("Redirect End User when provided with redirect parameters as part of upload file request") {

    val fileId = anyFileId
    val envelopeId = anyEnvelopeId

    scenario("Redirect upon success to valid url - only success url provided") {

      Given("Envelope created with default parameters")
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)

      When("a file is uploaded provided a redirect on success to a https://www-dev.tax.service.gov.uk url")
      val redirectSuccessUrl = "https://www-dev.tax.service.gov.uk/estimate-paye-take-home-pay/your-pay"
      val queryParam = s"redirect-success-url=$redirectSuccessUrl"
      val uploadFileResponse = uploadDummyFileWithRedirects(envelopeId, fileId, queryParam)

      Then("upon success the user should be redirected to the url specified in the query parameter")
      uploadFileResponse.status should be(301)
      uploadFileResponse.header("Location") shouldBe redirectSuccessUrl
    }

    scenario("Redirect upon success to valid url - both success and error urls provided") {

      Given("Envelope created with default parameters")
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)

      When("a file is uploaded provided a redirect on success to a https://www-dev.tax.service.gov.uk url")
      val redirectSuccessUrl = "https://www-dev.tax.service.gov.uk/estimate-paye-take-home-pay/your-pay"
      val redirectErrorUrl = "https://www-qa.tax.service.gov.uk/estimate-paye-take-home-pay/your-pay"
      val queryParam = s"redirect-success-url=$redirectSuccessUrl&redirect-error-url=$redirectErrorUrl"
      val uploadFileResponse = uploadDummyFileWithRedirects(envelopeId, fileId, queryParam)

      Then("upon success the user should be redirected to the url specified in the query parameter")
      uploadFileResponse.status should be(301)
      uploadFileResponse.header("Location") shouldBe redirectSuccessUrl
    }

//    scenario("Redirect upon error to valid url - both success and error urls provided") {
//
//      Given("An envelope which does not exist")
//      val invalidEnvelopeId: EnvelopeId = "123456789"
//
//      When("a file is uploaded provided a redirect on error to a https://www-dev.tax.service.gov.uk url")
//      val redirectSuccessUrl = "https://www-dev.tax.service.gov.uk/estimate-paye-take-home-pay/your-pay"
//      val redirectErrorUrl = "https://www-qa.tax.service.gov.uk/estimate-paye-take-home-pay/your-pay"
//      val queryParam = s"redirect-success-url=$redirectSuccessUrl&redirect-error-url=$redirectErrorUrl"
//      val uploadFileResponse = uploadDummyFileWithRedirects(invalidEnvelopeId, fileId, queryParam)
//
//      Then("upon success the user should be redirected to the url specified in the query parameter")
//      uploadFileResponse.status should be(301)
//      uploadFileResponse.header("Location") shouldBe redirectErrorUrl
//    }



    scenario("Redirect upon success to invalid url - not https") {
      Given("Envelope created with default parameters")
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)

      When("a file is uploaded provided a redirect on success to a https://www-dev.tax.service.gov.uk url")
      val redirectSuccessUrl = "http://www-dev.tax.service.gov.uk/estimate-paye-take-home-pay/your-pay"
      val queryParam = s"redirect-success-url=$redirectSuccessUrl"
      val uploadFileResponse = uploadDummyFileWithRedirects(envelopeId, fileId, queryParam)

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
      val redirectErrorUrl = "http://www-dev.tax.service.gov.uk/estimate-paye-take-home-pay/your-pay"
      val queryParam = s"redirect-error-url=$redirectErrorUrl"
      val uploadFileResponse = uploadDummyFileWithRedirects(envelopeId, fileId, queryParam)

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
      val redirectSuccessUrl = "https://www.playframework.com/documentation/2.5.x/ScalaTestingWithScalaTest"
      val queryParam = s"redirect-success-url=$redirectSuccessUrl"
      val uploadFileResponse = uploadDummyFileWithRedirects(envelopeId, fileId, queryParam)

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
      val redirectErrorUrl = "https://www.playframework.com/documentation/2.5.x/ScalaTestingWithScalaTest"
      val queryParam = s"redirect-error-url=$redirectErrorUrl"
      val uploadFileResponse = uploadDummyFileWithRedirects(envelopeId, fileId, queryParam)

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
