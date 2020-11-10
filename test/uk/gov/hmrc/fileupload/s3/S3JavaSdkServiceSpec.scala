/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.fileupload.s3

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.fileupload.FileId

class S3JavaSdkServiceSpec
  extends AnyWordSpecLike
     with Matchers {

  "dedupeFilenames" should {
    "dedupe" in {
      S3JavaSdkService.dedupeFilenames(files = List(
          FileId("1") -> Some("file"),
          FileId("2") -> Some("file"),
          FileId("3") -> Some("file")
         )).toSet shouldBe Set(
          FileId("1") -> "file",
          FileId("2") -> "file-1",
          FileId("3") -> "file-2"
         )
    }

    "preserve extension" in {
      S3JavaSdkService.dedupeFilenames(files = List(
          FileId("1") -> Some("file.pdf"),
          FileId("2") -> Some("file.pdf"),
          FileId("3") -> Some("file.pdf")
         )).toSet shouldBe Set(
          FileId("1") -> "file.pdf",
          FileId("2") -> "file-1.pdf",
          FileId("3") -> "file-2.pdf"
         )
    }

    "not affect file names when there are no duplicates" in {
      S3JavaSdkService.dedupeFilenames(files = List(
          FileId("1") -> Some("file1.pdf"),
          FileId("2") -> Some("file2.pdf"),
          FileId("3") -> Some("file3.pdf")
         )).toSet shouldBe Set(
          FileId("1") -> "file1.pdf",
          FileId("2") -> "file2.pdf",
          FileId("3") -> "file3.pdf"
         )
    }
  }
}
