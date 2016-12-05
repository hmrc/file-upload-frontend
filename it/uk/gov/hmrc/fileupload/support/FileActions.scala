package uk.gov.hmrc.fileupload.support

import play.api.Play.current
import play.api.libs.ws.{WS, WSResponse}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

trait FileActions extends ActionsSupport {

  def upload(data: Array[Byte], envelopeId: EnvelopeId, fileId: FileId): WSResponse =
    WS
      .url(s"$url/envelopes/$envelopeId/files/$fileId/content")
      .withHeaders("Content-Type" -> "application/octet-stream")
      .put(data)
      .futureValue

  def download(envelopeId: EnvelopeId, fileId: FileId): WSResponse =
    WS
      .url(s"$url/envelopes/$envelopeId/files/$fileId/content")
      .get()
      .futureValue

  def updateFileMetadata(data: String, envelopeId: EnvelopeId, fileId: FileId): WSResponse =
    WS
      .url(s"$url/envelopes/$envelopeId/files/$fileId/metadata" )
      .withHeaders("Content-Type" -> "application/json")
      .put(data.getBytes)
      .futureValue

  def getFileMetadataFor(envelopeId: EnvelopeId, fileId: FileId): WSResponse =
    WS
      .url(s"$url/envelopes/$envelopeId/files/$fileId/metadata")
      .get()
      .futureValue
}
