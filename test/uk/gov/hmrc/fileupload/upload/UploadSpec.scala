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
import play.api.mvc.Request
import play.api.test.FakeRequest
import uk.gov.hmrc.fileupload.DomainFixtures._
import uk.gov.hmrc.fileupload.transfer.TransferService.{EnvelopeAvailableServiceError, EnvelopeNotFoundError, TransferServiceError}
import uk.gov.hmrc.fileupload.upload.UploadService.{UploadServiceDownstreamError, UploadServiceEnvelopeNotFoundError}
import uk.gov.hmrc.fileupload.{EnvelopeId, File}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UploadSpec extends UnitSpec with ScalaFutures {

  "Uploading" should {

    val UnknownEnvelopeId = anyEnvelopeId
    val ErrorCausingEnvelopeId = anyEnvelopeId
    val CannotTransferEnvelopeId = anyEnvelopeId
    val anyRequest = FakeRequest("POST", "/test-only/create-envelope")


    val envelopeCheck = (envelopeId: EnvelopeId) => envelopeId match {
      case UnknownEnvelopeId => Future.successful(Xor.left(EnvelopeNotFoundError(envelopeId)))
      case ErrorCausingEnvelopeId => Future.successful(Xor.left(EnvelopeAvailableServiceError(envelopeId, "someEnvelopeExistsError")))
      case validEnvelopeId => Future.successful(Xor.right(envelopeId))
    }

    val transfer = (file: File, req: Request[_]) => file match {
      case File(_, _, _, _, CannotTransferEnvelopeId, _) => Future.successful(Xor.left(TransferServiceError(file.envelopeId, "someErrorTransferring")))
      case File(_, _, _, _, validEnvelopeId, _) => Future.successful(Xor.right(file.envelopeId))
    }

    val upload = UploadService.upload(envelopeCheck, transfer) _

    "success if the envelope exists and can transfer" in {
      val validEnvelopeId = anyEnvelopeId

      upload(anyFileFor(validEnvelopeId), anyRequest).futureValue shouldBe Xor.right(validEnvelopeId)
    }

    "error if the envelope does not exist" in {
      upload(anyFileFor(UnknownEnvelopeId), anyRequest).futureValue shouldBe
        Xor.left(UploadServiceEnvelopeNotFoundError(UnknownEnvelopeId))
    }

    "error if the envelope existence causes an error" in {
      upload(anyFileFor(ErrorCausingEnvelopeId), anyRequest).futureValue shouldBe
        Xor.left(UploadServiceDownstreamError(ErrorCausingEnvelopeId, "someEnvelopeExistsError"))
    }

    "error if the cannot transfer" in {
      upload(anyFileFor(CannotTransferEnvelopeId), anyRequest).futureValue shouldBe
        Xor.left(UploadServiceDownstreamError(CannotTransferEnvelopeId, "someErrorTransferring"))
    }
  }
}
