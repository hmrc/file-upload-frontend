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

package uk.gov.hmrc.fileupload.upload

import cats.data.Xor
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.EnvelopeId
import uk.gov.hmrc.fileupload.Fixtures.anyEnvelopeId
import uk.gov.hmrc.fileupload.transfer.Service.{EnvelopeAvailableEnvelopeNotFoundError, EnvelopeAvailableServiceError}
import uk.gov.hmrc.fileupload.upload.Service.UploadServiceError
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UploadSpec extends UnitSpec with ScalaFutures {

  "Uploading" should {

    val existingEnvelopeId = anyEnvelopeId
    val unknownEnvelopeId = anyEnvelopeId
    val errorCausingEnvelopeId = anyEnvelopeId

    val envelopeCheck = (envelopeId: EnvelopeId) => envelopeId match {
      case `existingEnvelopeId` => Future.successful(Xor.right(existingEnvelopeId))
      case `unknownEnvelopeId` => Future.successful(Xor.left(EnvelopeAvailableEnvelopeNotFoundError(envelopeId)))
      case `errorCausingEnvelopeId` => Future.successful(Xor.left(EnvelopeAvailableServiceError(envelopeId, "someError")))
    }

    "success if the envelope exists" in {
      Service.upload(envelopeCheck, null, null, null)(existingEnvelopeId).futureValue shouldBe
        Xor.right(existingEnvelopeId)
    }

    "error if the envelope does not exist" in {
      Service.upload(envelopeCheck, null, null, null)(unknownEnvelopeId).futureValue shouldBe
        Xor.left(UploadServiceError(unknownEnvelopeId, s"Envelope ID [${unknownEnvelopeId.value}] does not exist"))
    }

    "error if the envelope existance causes an error" in {
      Service.upload(envelopeCheck, null, null, null)(errorCausingEnvelopeId).futureValue shouldBe
        Xor.left(UploadServiceError(errorCausingEnvelopeId, "someError"))
    }
  }
}
