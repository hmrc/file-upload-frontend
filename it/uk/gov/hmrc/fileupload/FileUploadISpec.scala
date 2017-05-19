package uk.gov.hmrc.fileupload

import org.scalatest.{FeatureSpecLike, GivenWhenThen, Matchers}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.DomainFixtures._
import uk.gov.hmrc.fileupload.support._

class FileUploadISpec extends FeatureSpecLike with GivenWhenThen with FileActions with EnvelopeActions with Eventually with Matchers{

  feature("File upload front-end") {

    val fileId = anyFileId
    val envelopeId = anyEnvelopeId

    scenario("transfer a file to the back-end") {
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)

      val result = uploadDummyFile(envelopeId, fileId)

      result.status should be(200)

      Wiremock.quarantineFileCommandTriggered()
      eventually {
        Wiremock.scanFileCommandTriggered()
      }(PatienceConfig(timeout = Span(30, Seconds)))
      eventually {
        val res = download(envelopeId, fileId)
        res.status shouldBe 200
        res.body shouldBe "someTextContents"

      }(PatienceConfig(timeout = Span(30, Seconds)))
    }

    scenario("""Prevent uploading if envelope is not in "OPEN" state"""") {
      Wiremock.respondToEnvelopeCheck(envelopeId, body = ENVELOPE_CLOSED_RESPONSE)

      val repository = new ChunksMongoRepository(mongo)
      repository.removeAll().futureValue
      def numberOfChunks = repository.findAll().futureValue.size
      numberOfChunks shouldBe 0

      val result = uploadDummyFile(envelopeId, fileId)
      result.status should be(423)
      numberOfChunks shouldBe 0
    }

    scenario("""Ensure we continue to allow uploading if envelope is in "OPEN" state"""") {

      val secondFileId = anyFileId
      Wiremock.respondToEnvelopeCheck(envelopeId, body = ENVELOPE_OPEN_RESPONSE)

      val result = uploadDummyFile(envelopeId, secondFileId)
      result.status should be(200)

      eventually {
        val res = download(envelopeId, secondFileId)
        res.status shouldBe 200

      }(PatienceConfig(timeout = Span(30, Seconds)))
    }

  }



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



    scenario("Redirect upon success to invalid url - not https") {
      Given("Envelope created with default parameters")
      Wiremock.responseToUpload(envelopeId, fileId)
      Wiremock.respondToEnvelopeCheck(envelopeId)

      When("a file is uploaded provided a redirect on success to a https://www-dev.tax.service.gov.uk url")
      val redirectSuccessUrl = "http://www-dev.tax.service.gov.uk/estimate-paye-take-home-pay/your-pay"
      val queryParam = s"redirect-success-url=$redirectSuccessUrl"
      val uploadFileResponse = uploadDummyFileWithRedirects(envelopeId, fileId, queryParam)

      Then("a Bad Requst response should be received")
      uploadFileResponse.status should be(400)

      And("The message should indicate https required")
      val expectedMessage = "java.net.MalformedURLException: Https is required for the redirection."
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

      Then("upon success the user should be redirected to the url specified in the query parameter")
      uploadFileResponse.status should be(400)

      And("The message should indicate https required")
      val expectedMessage = "java.net.MalformedURLException: Https is required for the redirection."
      val parsedBody = Json.parse(uploadFileResponse.body)
      val message = (parsedBody \ "message").as[String]
      message shouldBe expectedMessage
    }



  }

}

