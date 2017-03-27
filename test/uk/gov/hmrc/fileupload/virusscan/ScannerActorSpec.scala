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

package uk.gov.hmrc.fileupload.virusscan

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import cats.data.Xor
import org.scalatest.Matchers
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import play.api.libs.json.Json
import uk.gov.hmrc.fileupload.notifier.NotifierService.NotifySuccess
import uk.gov.hmrc.fileupload.notifier.{CommandHandler, MarkFileAsClean, QuarantineFile}
import uk.gov.hmrc.fileupload.virusscan.ScanningService.ScanResultFileClean
import uk.gov.hmrc.fileupload.{EnvelopeId, FileId, FileRefId, StopSystemAfterAll}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class ScannerActorSpec extends TestKit(ActorSystem("scanner")) with ImplicitSender with UnitSpec with Matchers with Eventually with StopSystemAfterAll {

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(2, Seconds)), interval = scaled(Span(2, Seconds)))

  "ScannerActor" should {
    "scan files" in new ScanFixture {
      val eventsToSend = fillEvents(5)
      send(eventsToSend)

      eventually {
        collector shouldBe expectedCollector(eventsToSend)
      }

      collector = List.empty[Any]
      val newEventToSend = fillEvents(1)
      send(newEventToSend)

      eventually {
        collector shouldBe expectedCollector(newEventToSend)
      }
    }
  }

  trait ScanFixture {
    val envelopeId = EnvelopeId()
    val fileId = FileId()
    val fileRefId = FileRefId()

    def subscribe = (_: ActorRef, _: Class[_]) => true

    var collector = List.empty[Any]

    def scanBinaryData(envelopeId: EnvelopeId, fileId: FileId, fileRefId: FileRefId) = {
      Thread.sleep(100)
      collector = collector.::(fileRefId)
      Future.successful(Xor.right(ScanResultFileClean))
    }

    val commandHandler = new CommandHandler {
      def notify(command: AnyRef)(implicit ec: ExecutionContext) = {
        collector = collector.::(command)
        Future.successful(Xor.Right(NotifySuccess))
      }
    }

    def fillEvents(n: Int) = List.fill(n) {
      QuarantineFile(EnvelopeId(), FileId(), FileRefId(), 0, "name", "pdf", 10, Json.obj())
    }

    def expectedCollector(events: List[QuarantineFile]): List[Any] =
      events.reverse.flatMap(e => {
        List(MarkFileAsClean(e.id, e.fileId, e.fileRefId), e.fileRefId)
      })

    val actor = system.actorOf(ScannerActor.props(subscribe, scanBinaryData, commandHandler))

    def send(eventsToSend: List[QuarantineFile]) =
      eventsToSend.foreach {
        actor ! _
    }
  }
}
