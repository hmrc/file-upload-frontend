package uk.gov.hmrc.fileupload.support

import java.io.File

import org.scalatest.Suite
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.fileupload.EnvelopeId

import scala.io.Source

trait EnvelopeActions extends ActionsSupport {
  this: Suite =>

  def createEnvelope(file: File): WSResponse = createEnvelope(Source.fromFile(file).mkString)

  def createEnvelope(data: String): WSResponse = createEnvelope(data.getBytes())

  def createEnvelope(data: Array[Byte]): WSResponse =
    client
      .url(s"$url/envelope")
      .withHeaders("Content-Type" -> "application/json")
      .post(data)
      .futureValue

  def getEnvelopeFor(id: EnvelopeId): WSResponse =
    client
      .url(s"$url/envelopes/$id")
      .get()
      .futureValue

  def envelopeIdFromHeader(response: WSResponse): EnvelopeId = {
    val locationHeader = response.header("Location").get
    EnvelopeId(locationHeader.substring(locationHeader.lastIndexOf('/') + 1))
  }

  def createEnvelope(): EnvelopeId = {
    val response: WSResponse = createEnvelope(EnvelopeReportSupport.requestBody())
    val locationHeader = response.header("Location").get
    EnvelopeId(locationHeader.substring(locationHeader.lastIndexOf('/') + 1))
  }

  def deleteEnvelopFor(id: EnvelopeId): WSResponse =
    client
      .url(s"$url/envelopes/$id")
      .delete()
      .futureValue

}
