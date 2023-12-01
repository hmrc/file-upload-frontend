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

import play.api.libs.json.{Json, JsValue}

object EnvelopeReportSupport extends Support {

  def requestBodyAsJson(args: Map[String, Any] = Map.empty): JsValue =
    Json.parse(requestBody(args))

  def requestBody(args: Map[String, Any] = Map.empty): String =
    s"""
     |{
     |  "constraints": {
     |    "contentTypes": [
     |      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
     |      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
     |      "application/vnd.oasis.opendocument.spreadsheet"
     |    ],
     |    "maxItems": 100,
     |    "maxSize": "12GB",
     |    "maxSizePerItem": "10MB"
     |  },
     |  "callbackUrl": "http://absolute.callback.url",
     |  "expiryDate": "${args.getOrElse("formattedExpiryDate", "2099-07-14T10:28:18Z")}",
     |  "metadata": {
     |    "anything": "the caller wants to add to the envelope"
     |  }
     |}
		 """.stripMargin

  def responseBodyAsJson(id: String, args: Map[String, Any] = Map.empty): JsValue =
    Json.parse(responseBody(id, args))

  def responseBody(id: String, args: Map[String, Any] = Map.empty): String =
    s"""
    |{
    |  "id": "$id",
    |  "constraints": {
    |    "contentTypes": [
    |      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    |      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    |      "application/vnd.oasis.opendocument.spreadsheet"
    |    ],
    |    "maxItems": 100,
    |    "maxSize": "12GB",
    |    "maxSizePerItem": "10MB"
    |  },
    |  "callbackUrl": "http://absolute.callback.url",
    |  "expiryDate": "${args.getOrElse("formattedExpiryDate", "2099-07-14T10:28:18Z") }",
    |  "metadata": {
    |    "anything": "the caller wants to add to the envelope"
    |  }
    |}
    """.stripMargin
}
