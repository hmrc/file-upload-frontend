/*
 * Copyright 2023 HM Revenue & Customs
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

import java.net.URLEncoder

import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.http.HttpVerbs.POST
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse, writeableOf_JsValue}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.fileupload.ApplicationModule
import uk.gov.hmrc.fileupload.s3.S3JavaSdkService
import uk.gov.hmrc.fileupload.testonly.CreateEnvelopeRequest.ContentTypes
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TestOnlyController @Inject()(
  appModule    : ApplicationModule,
  wSClient     : WSClient,
  val s3Service: S3JavaSdkService,
  mcc          : MessagesControllerComponents
)(implicit
  val ec: ExecutionContext
) extends FrontendController(mcc)
     with S3TestController {

  val baseUrl                    = appModule.fileUploadBackendBaseUrl
  val optTestOnlySdesStubBaseUrl = appModule.optTestOnlySdesStubBaseUrl

  def createEnvelope() = Action.async(parse.json[CreateEnvelopeRequest]) { implicit request =>
    def extractEnvelopeId(response: WSResponse): String =
      response
        .header("Location")
        .map(l => l.substring(l.lastIndexOf("/") + 1))
        .getOrElse("missing/invalid")

    val payload = Json.toJson(request.body)

    wSClient.url(s"$baseUrl/file-upload/envelopes").post(payload).map { response =>
      if (response.status >= 200 && response.status <= 299)
        Created(Json.obj("envelopeId" -> extractEnvelopeId(response)))
      else
        InternalServerError(
          Json.obj("upstream_status" -> response.status, "error_message" -> response.statusText)
        )
    }
  }

  def getEnvelope(envelopeId: String) = Action.async {
    wSClient.url(s"$baseUrl/file-upload/envelopes/$envelopeId").get().map { response =>
      new Status(response.status)(response.body).withHeaders(
        "Content-Type" -> response.header("Content-Type").getOrElse("unknown")
      )
    }
  }

  def downloadFile(envelopeId: String, fileId: String) = Action.async {
    wSClient.url(s"$baseUrl/file-upload/envelopes/$envelopeId/files/$fileId/content").get().map {
      resultFromBackEnd => if (resultFromBackEnd.status == 200) {
        Ok(resultFromBackEnd.bodyAsBytes).withHeaders(
          "Content-Length" -> resultFromBackEnd.header("Content-Length").getOrElse("unknown"),
          "Content-Disposition" -> resultFromBackEnd.header("Content-Disposition").getOrElse("unknown")
        )
      } else Ok(resultFromBackEnd.json)
    }
  }

  def routingRequests() = Action.async(parse.json) { implicit request =>
    wSClient.url(s"$baseUrl/file-routing/requests").post(request.body).map { response =>
      new Status(response.status)(response.body)
    }
  }

  def transferGetEnvelopes(destination: Option[String]) = Action.async {
    val transferUrl = s"$baseUrl/file-transfer/envelopes"
    val wsUrl = destination match {
      case Some(d)  => wSClient.url(transferUrl).addQueryStringParameters(("destination", URLEncoder.encode(d, "UTF-8")))
      case None     => wSClient.url(transferUrl)
    }
    wsUrl.get().map { response =>
      val body = Json.parse(response.body)
      if (response.status != 200)
        InternalServerError(s"$body backendStatus:${response.status}")
      else
        Ok(body)
    }
  }

  def transferDownloadEnvelope(envelopeId: String) = Action.async {
    wSClient.url(s"$baseUrl/file-transfer/envelopes/$envelopeId").get().flatMap {
      resultFromBackEnd => if (resultFromBackEnd.status == 200) {
        Future.successful(Ok(resultFromBackEnd.bodyAsBytes).withHeaders(
          "Content-Type" -> resultFromBackEnd.header("Content-Type").getOrElse("unknown"),
          "Content-Disposition" -> resultFromBackEnd.header("Content-Disposition").getOrElse("unknown")
        ))
      } else Future.successful(Ok(resultFromBackEnd.json))
    }
  }

  def transferDeleteEnvelope(envelopeId: String) = Action.async {
    wSClient.url(s"$baseUrl/file-transfer/envelopes/$envelopeId").delete().map { response =>
      new Status(response.status)(response.body)
    }
  }

  def getEvents(streamId: String) = Action.async {
    wSClient.url(s"$baseUrl/file-upload/events/$streamId").get().map { response =>
      new Status(response.status)(response.body).withHeaders {
        "Content-Type" -> response.header("Content-Type").getOrElse("unknown")
      }
    }
  }

  def filesInProgress() = Action.async {
    wSClient.url(s"$baseUrl/file-upload/files/inprogress").get().map { response =>
      Ok(Json.parse(response.body))
    }
  }

  def recreateAllCollections() = Action.async {
    wSClient.url(s"$baseUrl/file-upload/test-only/recreate-collections").post(Json.obj()).map {
      response => new Status(response.status)(response.body)
    }
  }

  def configureFileReadyNotification(): Action[AnyContent] = Action.async { implicit request =>
    optTestOnlySdesStubBaseUrl.fold(Future.successful(NotImplemented("sdes-stub is not enabled for this deployment"))) { sdesStubBaseUrl =>
      val params = request.queryString.toSeq.flatMap { case (name, values) =>
        values.map(name -> _)
      }
      wSClient.url(s"$sdesStubBaseUrl/sdes-stub/configure/notification/fileready")
        .withQueryStringParameters(params: _*)
        .execute(POST)
        .map { response =>
          new Status(response.status)(response.body)
        }
    }
  }
}

case class EnvelopeConstraintsUserSetting(
  maxItems      : Option[Int]                = None,
  maxSize       : Option[String]             = None,
  maxSizePerItem: Option[String]             = None,
  contentTypes  : Option[List[ContentTypes]] = None
)

case class CreateEnvelopeRequest(
  callbackUrl: Option[String]                         = None,
  expiryDate : Option[DateTime]                       = None,
  metadata   : Option[JsObject]                       = None,
  constraints: Option[EnvelopeConstraintsUserSetting] = None
)

object CreateEnvelopeRequest {
  type ContentTypes = String
  implicit val createEnvelopeRequestReads: Reads[CreateEnvelopeRequest] = EnvelopeRequestReads
  implicit val createEnvelopeRequestWrites: Writes[CreateEnvelopeRequest] = EnvelopeRequestWrites
}

object EnvelopeRequestWrites extends Writes[CreateEnvelopeRequest] {
  override def writes(s: CreateEnvelopeRequest): JsValue = {
    implicit val dateWrites: Writes[DateTime] = {
      val pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'"
      val df      = org.joda.time.format.DateTimeFormat.forPattern(pattern)
      Writes[DateTime] { d => JsString(d.toString(df)) }
    }
    val envelopeConstraintsUserSetting = s.constraints.getOrElse(EnvelopeConstraintsUserSetting(None,None,None,None))
    Json.obj("expiryDate" -> s.expiryDate,
             "metadata" -> s.metadata,
             "callbackUrl" -> s.callbackUrl,
             "constraints" -> Json.obj("contentTypes" -> envelopeConstraintsUserSetting.contentTypes,
                                       "maxItems" -> envelopeConstraintsUserSetting.maxItems,
                                       "maxSize" -> envelopeConstraintsUserSetting.maxSize,
                                       "maxSizePerItem" -> envelopeConstraintsUserSetting.maxSizePerItem
                              )
    )
  }
}

object EnvelopeRequestReads extends Reads[CreateEnvelopeRequest] {
  implicit val dateReads: Reads[DateTime] = new Reads[DateTime] {
    val pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    val df      = org.joda.time.format.DateTimeFormat.forPattern(pattern)
    def reads(json: JsValue): JsResult[DateTime] =
      json match {
        case JsNumber(d) => JsSuccess(new DateTime(d.toLong))
        case JsString(s) => scala.util.Try(DateTime.parse(s, df)).toOption match {
                              case Some(d) => JsSuccess(d)
                              case _       => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.jodadate.format", pattern))))
                            }
        case _ => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.date"))))
      }
  }
  implicit val constraintsFormats: OFormat[EnvelopeConstraintsUserSetting] = Json.format[EnvelopeConstraintsUserSetting]

  override def reads(value: JsValue): JsSuccess[CreateEnvelopeRequest] =
    JsSuccess(CreateEnvelopeRequest(
      (value \ "callbackUrl").validate[String].asOpt,
      (value \ "expiryDate" ).validate[DateTime].asOpt,
      (value \ "metadata"   ).validate[JsObject].asOpt,
      (value \ "constraints").validate[EnvelopeConstraintsUserSetting].asOpt
    ))
}
