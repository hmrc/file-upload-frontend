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

package uk.gov.hmrc.fileupload.quarantine

import cats.data.Xor
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.{EnvelopeId, FileId}
import uk.gov.hmrc.fileupload.quarantine.Repository.WriteFileNotPersistedError
import uk.gov.hmrc.fileupload.quarantine.Service.QuarantineUploadServiceError
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class ServiceSpec extends UnitSpec with ScalaFutures {

  implicit val ec = ExecutionContext.global

  "create" should {
    "be successful" in {
      val file = new File(null, "test.pdf", None, EnvelopeId("abc"), FileId("123"))
      val upload = Service.upload(_ => Future.successful(Xor.Right(file.envelopeId))) _

      val result = upload(file).futureValue

      result shouldBe Xor.Right(file.envelopeId)
    }

    "be not successful when writeFile is not successful" in {
      val file = new File(null, "test.pdf", None, EnvelopeId("abc"), FileId("123"))
      val upload = Service.upload(_ => Future.successful(Xor.Left(WriteFileNotPersistedError(file.envelopeId)))) _

      val result = upload(file).futureValue

      result shouldBe Xor.Left(QuarantineUploadServiceError(file.envelopeId, "File not persisted"))
    }

    "be not successful when future fails" in {
      val file = new File(null, "test.pdf", None, EnvelopeId("abc"), FileId("123"))
      val upload = Service.upload(_ => Future.failed(new Exception("not good"))) _

      val result = upload(file).futureValue

      result shouldBe Xor.left(QuarantineUploadServiceError(file.envelopeId, "not good"))
    }
  }

}
