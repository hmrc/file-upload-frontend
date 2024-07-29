/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.support

import org.scalatest.Suite
import play.api.libs.ws.{WSResponse, writeableOf_ByteArray, writeableOf_String}
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

trait FileActions extends ActionsSupport {
  this: Suite =>

  private val httpSeparator = "\r\n"
  private val actualBoundary = "-----011000010111000001101001"
  private val endBoundary = s"$httpSeparator$actualBoundary--"
  private val acceptedFileHeader =
    """Content-Disposition: form-data; name="file1"; filename="test.pdf"; Content-Type: "application/pdf""""
  private val shouldNotAcceptedFileHeader =
    """Content-Disposition: form-data; name="file1"; filename="test.txt"; Content-Type: "text/plain""""
  private val textContent = "someTextContents"

  val file: String = s"$actualBoundary$httpSeparator" +
                     s"$acceptedFileHeader" +
                     s"$httpSeparator$httpSeparator" +
                     s"$textContent$endBoundary"

  val tooLargeFile: String = s"$actualBoundary$httpSeparator" +
                             s"$acceptedFileHeader" +
                             s"$httpSeparator$httpSeparator" +
                             s"${textContent * 1024 * 1024}$endBoundary"

  val wrongTypeFile: String = s"$actualBoundary$httpSeparator" +
                              s"$shouldNotAcceptedFileHeader" +
                              s"$httpSeparator$httpSeparator" +
                              s"$textContent$endBoundary"

  def upload(data: Array[Byte], envelopeId: EnvelopeId, fileId: FileId): WSResponse =
    wsClient
      .url(s"$url/envelopes/$envelopeId/files/$fileId/content")
      .withHttpHeaders("Content-Type" -> "application/octet-stream")
      .put(data)
      .futureValue

  def download(envelopeId: EnvelopeId, fileId: FileId): WSResponse =
    wsClient
      .url(s"$internalUrl/download/envelopes/$envelopeId/files/$fileId")
      .get()
      .futureValue

  def updateFileMetadata(data: String, envelopeId: EnvelopeId, fileId: FileId): WSResponse =
    wsClient
      .url(s"$url/envelopes/$envelopeId/files/$fileId/metadata")
      .withHttpHeaders("Content-Type" -> "application/json")
      .put(data.getBytes)
      .futureValue

  def getFileMetadataFor(envelopeId: EnvelopeId, fileId: FileId): WSResponse =
    wsClient
      .url(s"$url/envelopes/$envelopeId/files/$fileId/metadata")
      .get()
      .futureValue

  def uploadDummyFile(envelopeId: EnvelopeId, fileId: FileId): WSResponse =
    wsClient.url(s"$url/upload/envelopes/$envelopeId/files/$fileId")
      .withHttpHeaders(
        "Content-Type" -> "multipart/form-data; boundary=---011000010111000001101001",
        "X-Request-ID" -> "someId",
        "X-Session-ID" -> "someId",
        "X-Requested-With" -> "someId"
      )
      .post(file)
      .futureValue

  def uploadDummyFileWithoutRedirects(envelopeId: EnvelopeId, fileId: FileId, redirectParams: String): WSResponse =
    wsClient.url(s"$url/upload/envelopes/$envelopeId/files/$fileId?$redirectParams")
      .withFollowRedirects(false)
      .withHttpHeaders(
        "Content-Type" -> "multipart/form-data; boundary=---011000010111000001101001",
        "X-Request-ID" -> "someId",
        "X-Session-ID" -> "someId",
        "X-Requested-With" -> "someId"
      )
      .post(file)
      .futureValue

  def uploadDummyLargeFile(envelopeId: EnvelopeId, fileId: FileId): WSResponse =
    wsClient.url(s"$url/upload/envelopes/$envelopeId/files/$fileId")
      .withHttpHeaders(
        "Content-Type" -> "multipart/form-data; boundary=---011000010111000001101001",
        "X-Request-ID" -> "someId",
        "X-Session-ID" -> "someId",
        "X-Requested-With" -> "someId"
      )
      .post(tooLargeFile)
      .futureValue

  def uploadDummyUnsupportedContentTypeFile(envelopeId: EnvelopeId, fileId: FileId): WSResponse =
    wsClient.url(s"$url/upload/envelopes/$envelopeId/files/$fileId")
      .withHttpHeaders(
        "Content-Type" -> "multipart/form-data; boundary=---011000010111000001101001",
        "X-Request-ID" -> "someId",
        "X-Session-ID" -> "someId",
        "X-Requested-With" -> "someId"
      )
      .post(wrongTypeFile)
      .futureValue
}
