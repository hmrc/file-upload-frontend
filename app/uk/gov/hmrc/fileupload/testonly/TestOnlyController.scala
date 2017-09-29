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

package uk.gov.hmrc.fileupload.testonly

import akka.util.ByteString
import org.joda.time.DateTime
import play.api.libs.iteratee.Iteratee
import play.api.libs.json._
import play.api.libs.streams.{Accumulator, Streams}
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc._
import uk.gov.hmrc.fileupload.s3.S3JavaSdkService
import uk.gov.hmrc.fileupload.testonly.CreateEnvelopeRequest.{ByteStream, ContentTypes}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

class TestOnlyController(baseUrl: String, recreateCollections: () => Unit, wSClient: WSClient, val s3Service: S3JavaSdkService)
   extends Controller with S3TestController {


  def createEnvelope() = Action.async(jsonBodyParser[CreateEnvelopeRequest]) { implicit request =>
    def extractEnvelopeId(response: WSResponse): String =
      response
        .allHeaders
        .get("Location")
        .flatMap(_.headOption)
        .map(l => l.substring(l.lastIndexOf("/") + 1))
        .getOrElse("missing/invalid")

    val payload = Json.toJson(request.body)

    wSClient.url(s"$baseUrl/file-upload/envelopes").post(payload).map { response =>
      Created(Json.obj("envelopeId" -> extractEnvelopeId(response)))
    }
  }

  def getEnvelope(envelopeId: String) = Action.async { implicit request =>
    wSClient.url(s"$baseUrl/file-upload/envelopes/$envelopeId").get().map { response =>
      new Status(response.status)(response.body).withHeaders(
        "Content-Type" -> response.allHeaders("Content-Type").head
      )
    }
  }

  def downloadFile(envelopeId: String, fileId: String) = Action.async { implicit request =>
    wSClient.url(s"$baseUrl/file-upload/envelopes/$envelopeId/files/$fileId/content").getStream().map {
      case (headers, enumerator) => Ok.feed(enumerator).withHeaders(
        "Content-Length" -> headers.headers("Content-Length").head,
        "Content-Disposition" -> headers.headers("Content-Disposition").head)
    }
  }

  def routingRequests() = Action.async(parse.json) { implicit request =>
    wSClient.url(s"$baseUrl/file-routing/requests").post(request.body).map { response =>
      new Status(response.status)(response.body)
    }
  }

  def transferGetEnvelopes() = Action.async { implicit request =>
    wSClient.url(s"$baseUrl/file-transfer/envelopes").get().map { response =>
      val body = Json.parse(response.body)
      if(response.status!=200) InternalServerError(body + s" backendStatus:${response.status}")
      else Ok(body)
    }
  }

  def transferDownloadEnvelope(envelopeId: String) = Action.async { implicit request =>
    wSClient.url(s"$baseUrl/file-transfer/envelopes/$envelopeId").get().flatMap {
      resultFromBackEnd ⇒ if (resultFromBackEnd.status == 200) {
        Future.successful(Ok(resultFromBackEnd.body))
      } else Future.successful(Ok(resultFromBackEnd.json))
    }
  }

  def transferDeleteEnvelope(envelopeId: String) = Action.async { implicit request =>
    wSClient.url(s"$baseUrl/file-transfer/envelopes/$envelopeId").delete().map { response =>
      new Status(response.status)(response.body)
    }
  }

  def getEvents(streamId: String) = Action.async { implicit request =>
    wSClient.url(s"$baseUrl/file-upload/events/$streamId").get().map { response =>
      new Status(response.status)(response.body).withHeaders {
        "Content-Type" -> response.allHeaders("Content-Type").head
      }
    }
  }

  def filesInProgress() = Action.async { implicit request =>
    wSClient.url(s"$baseUrl/file-upload/files/inprogress").get().map { response =>
      Ok(Json.parse(response.body))
    }
  }

  def recreateAllCollections() = Action.async {
    recreateCollections()
    wSClient.url(s"$baseUrl/file-upload/test-only/recreate-collections").post(Json.obj()).map {
      response => new Status(response.status)(response.body)
    }
  }

  def jsonBodyParser[A : Reads]: BodyParser[A] = new BodyParser[A] {
    def apply(v1: RequestHeader): Accumulator[ByteString, Either[Result, A]] = {
      iterateeToAccumulator(Iteratee.consume[Array[Byte]]()).map { data =>
        Try(Json.parse(data).validate[A]) match {
          case Success(JsSuccess(a, _)) => Right(a)
          case Success(JsError(errors)) => Left(BadRequest(s"$errors"))
          case Failure(NonFatal(ex)) => Left(BadRequest(s"$ex"))
        }
      }
    }
  }


  def iterateeToAccumulator[T](iteratee: Iteratee[ByteStream, T]): Accumulator[ByteString, T] = {
    val sink = Streams.iterateeToAccumulator(iteratee).toSink
    Accumulator(sink.contramap[ByteString](_.toArray[Byte]))
  }
}

case class EnvelopeConstraintsUserSetting(maxItems: Option[Int] = None,
                                          maxSize: Option[String] = None,
                                          maxSizePerItem: Option[String] = None,
                                          contentTypes: Option[List[ContentTypes]] = None)

case class CreateEnvelopeRequest(callbackUrl: Option[String] = None,
                                 expiryDate: Option[DateTime] = None,
                                 metadata: Option[JsObject] = None,
                                 constraints: Option[EnvelopeConstraintsUserSetting] = None)

object CreateEnvelopeRequest {
  type ByteStream = Array[Byte]
  type ContentTypes = String
  implicit val createEnvelopeRequestReads: Reads[CreateEnvelopeRequest] = EnvelopeRequestReads
  implicit val createEnvelopeRequestWrites: Writes[CreateEnvelopeRequest] = EnvelopeRequestWrites
}

object EnvelopeRequestWrites extends Writes[CreateEnvelopeRequest] {
  override def writes(s: CreateEnvelopeRequest): JsValue = {
    val envelopeConstraintsUserSetting = s.constraints.getOrElse(EnvelopeConstraintsUserSetting(None,None,None,None))
    Json.obj("expiryDate" -> s.expiryDate,
             "metadata" -> s.metadata,
             "callbackUrl" -> s.callbackUrl,
             "constraints" -> Json.obj("contentTypes" → envelopeConstraintsUserSetting.contentTypes,
                                       "maxItems" → envelopeConstraintsUserSetting.maxItems,
                                       "maxSize" → envelopeConstraintsUserSetting.maxSize,
                                       "maxSizePerItem" → envelopeConstraintsUserSetting.maxSizePerItem
                              )
    )
  }
}

object EnvelopeRequestReads extends Reads[CreateEnvelopeRequest] {
  implicit val dateReads: Reads[DateTime] = Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val dateWrites: Writes[DateTime] = Writes.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss'Z'")
  implicit val constraintsFormats: OFormat[EnvelopeConstraintsUserSetting] = Json.format[EnvelopeConstraintsUserSetting]

  override def reads(value: JsValue): JsSuccess[CreateEnvelopeRequest] = {
    JsSuccess(CreateEnvelopeRequest(
                (value \ "callbackUrl").validate[String].asOpt,
                (value \ "expiryDate").validate[DateTime].asOpt,
                (value \ "metadata").validate[JsObject].asOpt,
                (value \ "constraints").validate[EnvelopeConstraintsUserSetting].asOpt
              )
    )
  }
}