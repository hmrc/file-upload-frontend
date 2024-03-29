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

package uk.gov.hmrc.fileupload.virusscan

import org.apache.pekko.actor.{ActorRef, ActorSystem}
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId}
import uk.gov.hmrc.fileupload.notifier.{MarkFileAsClean, MarkFileAsInfected}
import uk.gov.hmrc.fileupload.s3.{S3KeyName, S3Service}

class DeletionActorSpec
  extends TestKit(ActorSystem("deletion"))
     with ImplicitSender
     with AnyWordSpecLike
     with Matchers
     with Eventually
     with IntegrationPatience {

  "DeletionActor" should {

    "delete infected files" in {
      def sendToActor(actor: ActorRef, eventsToSend: List[MarkFileAsInfected]) = {
        eventsToSend foreach { event =>
          actor ! event
        }
      }

      var collector: List[String] = List.empty
      val infectedEventsToSend = List.fill(5) {
        MarkFileAsInfected(EnvelopeId(), FileId(), FileRefId())
      }

      val deletion: S3Service.DeleteFileFromQuarantineBucket = { (s3Key: S3KeyName) =>
        Thread.sleep(100)
        collector = collector :+ s3Key.value
      }

      val createS3Key: (EnvelopeId, FileId) => S3KeyName =
        (e: EnvelopeId, f: FileId) => S3KeyName(f.value)

      val actor = system.actorOf(DeletionActor.props((_: ActorRef, _: Class[_]) => true, deletion, createS3Key))

      sendToActor(actor, infectedEventsToSend)

      eventually {
        collector shouldBe infectedEventsToSend.map(_.fileId.value)
      }

      val cleanEventToSendOne = MarkFileAsClean(EnvelopeId(), FileId(), FileRefId())
      val cleanEventToSendTwo = MarkFileAsClean(EnvelopeId(), FileId(), FileRefId())

      actor ! cleanEventToSendOne
      actor ! cleanEventToSendTwo

      eventually {
        collector shouldBe infectedEventsToSend.map(_.fileId.value)
      }
    }
  }
}
