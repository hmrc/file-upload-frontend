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

package uk.gov.hmrc.fileupload

import java.util.UUID

import play.api.libs.json.{JsString, JsValue}
import play.api.mvc.MultipartFormData
import play.api.mvc.MultipartFormData.FilePart
import play.api.test.{FakeHeaders, FakeRequest}
import reactivemongo.json.JSONSerializationPack
import reactivemongo.json.JSONSerializationPack._
import uk.gov.hmrc.fileupload.fileupload.JSONReadFile

import scala.concurrent.Future

object RestFixtures {

  case class TestJsonReadFile(id: JsValue = JsString("testid"), filename: Option[String] = None) extends JSONReadFile {
    val pack = JSONSerializationPack
    val contentType: Option[String] = None
    val chunkSize: Int = 0
    val length: Long = 0
    val uploadDate: Option[Long] = None
    val md5: Option[String] = None
    val metadata: Document = null
  }

  def uploadRequest(multipartBody: MultipartFormData[Future[JSONReadFile]]) = {
    FakeRequest(method = "POST", uri = "/upload", headers = FakeHeaders(), body = multipartBody)
  }

  def filePart(key: String, filename: String, contentType: Option[String]): FilePart[Future[JSONReadFile]] = {
    MultipartFormData.FilePart(key, filename, contentType,
      Future.successful(TestJsonReadFile(id = JsString(UUID.randomUUID().toString), filename = Some(filename))))
  }

  def validUploadRequest(files: File*) = {
    uploadRequest(MultipartFormData(Map(),
      files.map(file => filePart(file.filename, file.filename, file.contentType)),
      Seq.empty, Seq.empty))
  }

  def multipartFormData(dataParts: Map[String, Seq[String]]) =
    uploadRequest(MultipartFormData(dataParts, files = List(), badParts = List(), missingFileParts = List()))
}
