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

package uk.gov.hmrc.fileupload.controllers

import org.apache.pekko.util.ByteString
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.http.HttpEntity
import play.api.libs.json.{__, Json, JsObject, JsSuccess, JsError, JsValue, Reads}
import play.api.mvc.{AnyContent, Action, MessagesControllerComponents, ResponseHeader, Result}
import uk.gov.hmrc.fileupload.{ApplicationModule, EnvelopeId, FileId}
import uk.gov.hmrc.fileupload.s3.{MissingFileException, S3KeyName, ZipData}
import uk.gov.hmrc.fileupload.s3.S3Service.DownloadFromBucket
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileDownloadController @Inject()(
  appModule: ApplicationModule,
  mcc      : MessagesControllerComponents
)(implicit
  ec: ExecutionContext
) extends FrontendController(mcc) {

  private val logger = Logger(getClass)

  val downloadFromTransient : DownloadFromBucket                                              = appModule.downloadFromTransient
  val createS3Key           : (EnvelopeId, FileId) => S3KeyName                               = appModule.createS3Key
  val now                   : () => Long                                                      = appModule.now
  val downloadFromQuarantine: DownloadFromBucket                                              = appModule.downloadFromQuarantine
  val zipAndPresign         : (EnvelopeId, List[(FileId, Option[String])]) => Future[ZipData] = appModule.zipAndPresign

  def download(envelopeId: EnvelopeId, fileId: FileId): Action[AnyContent] = {
    logger.info(s"downloading a file from S3 with envelopeId: $envelopeId fileId: $fileId")
    downloadFileFromBucket(downloadFromTransient)(envelopeId, fileId)
  }

  def illegalDownloadFromQuarantine(envelopeId: EnvelopeId, fileId: FileId): Action[AnyContent] = {
    logger.error(s"downloading a file from S3 QUARANTINE with envelopeId: $envelopeId fileId: $fileId")
    downloadFileFromBucket(downloadFromQuarantine)(envelopeId, fileId)
  }

  def downloadFileFromBucket(fromBucket: DownloadFromBucket)(envelopeId: EnvelopeId, fileId: FileId) = Action {
    val key = createS3Key(envelopeId, fileId)
    fromBucket(key) match {
      case Some(result) =>
        logger.info(s"download result: contentType: ${result.metadata.contentType} & length: " +
          s"${result.metadata.contentLength}, metadata: ${result.metadata.s3Metadata}")

        Result(
          header = ResponseHeader(200, Map.empty),
          body = HttpEntity.Streamed(
            result.stream,
            Some(result.metadata.contentLength),
            Some(result.metadata.contentType))
        )
      case None => Result(
        header = ResponseHeader(404, Map.empty),
        body = HttpEntity.Strict(ByteString("{\"msg\":\"File not found\"}"), Some("application/json"))
      )
    }
  }

  def zip(envelopeId: EnvelopeId): Action[JsValue] = Action.async(parse.json) { request =>
    logger.info(s"zipping files for envelopeId: $envelopeId")
    implicit val zrr = ZipRequest.reads
    request.body.validate[ZipRequest] match {
      case JsSuccess(zipRequest, _) =>
        zipAndPresign(envelopeId, zipRequest.files).map { zipData =>
          implicit val zdw = ZipData.writes
          Ok(Json.toJson(zipData))
        }
        .recover {
          case e: MissingFileException =>
            logger.warn(s"could not zip files for envelopeId $envelopeId - missing files")
            Gone(e.getMessage())
        }
      case JsError(errors) => Future.successful(BadRequest(s"$errors"))
    }
  }
}

case class ZipRequest(
  files: List[(FileId, Option[String])]
)

object ZipRequest {
  val reads: Reads[ZipRequest] =
    Reads.at[JsObject](__ \ "files")
      .map(_.fieldSet.map { case (k, v) => FileId(k) -> v.asOpt[String] }.toList)
      .map(ZipRequest.apply)
}
