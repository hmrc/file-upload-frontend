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

package uk.gov.hmrc.fileupload_deprecated.connectors

import java.nio.ByteBuffer

import com.ning.http.client.{AsyncHttpClient, RequestBuilder}
import com.ning.http.client.providers.netty.FeedableBodyGenerator
import play.api.libs.iteratee.Iteratee
import play.api.libs.ws.WS
import uk.gov.hmrc.fileupload_deprecated.Errors.EnvelopeValidationError
import uk.gov.hmrc.fileupload_deprecated.WSHttp
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpResponse}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class FileUploadConnector extends ServicesConfig {

  import scala.concurrent.ExecutionContext.Implicits.global

  val http: HttpGet = WSHttp
  val baseUrl: String = baseUrl("file-upload")

  def okStatusToSuccess[A](envelopeId: A): PartialFunction[HttpResponse, Try[A]] = { case r if r.status == 200 => Success(envelopeId) }
  def envelopeFailure[A](envelopeId: String): PartialFunction[Throwable, Try[A]] = { case _ => Failure[A](EnvelopeValidationError(envelopeId)) }

  def validate(envelopeId: String)(implicit hc: HeaderCarrier): Future[Try[String]] = {
    http.GET(s"$baseUrl/file-upload/envelope/$envelopeId") map okStatusToSuccess(envelopeId) recover envelopeFailure(envelopeId)
  }


  // SEE: http://zhpooer.github.io/2014/08/20/play-for-scala-web-services-&-iteratees-&-websockets/
  def putFile(fileData: FileData)(implicit hc: HeaderCarrier): Future[Try[Boolean]] = {
    import play.api.Play.current

    val client: AsyncHttpClient = WS.client.underlying

    val bodyGenerator = new FeedableBodyGenerator()

    val request = new RequestBuilder("PUT")
      .setUrl(s"$baseUrl/file-upload/envelope/${fileData.envelopeId}/file/${fileData.fileId}/content")
      .setBody(bodyGenerator)
      .build()

    val responseFuture = client.executeRequest(request)

    val it = Iteratee.fold[Array[Byte], FeedableBodyGenerator](bodyGenerator) { (generator, bytes) =>
      val isLast = false
      generator.feed(ByteBuffer.wrap(bytes), isLast)
      generator
    } map { generator =>
      val isLast = true
      generator.feed(ByteBuffer.wrap(Array[Byte]()), isLast)

      val res = responseFuture.get()

      res.getStatusCode match {
        case 200 => Success(true)
        case _ => Failure(EnvelopeValidationError(fileData.envelopeId))
      }
    }

    fileData.data |>>> it
  }
}
