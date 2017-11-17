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

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.pipe
import cats.data.Xor
import play.api.Logger
import uk.gov.hmrc.fileupload.notifier.{CommandHandler, MarkFileAsClean, MarkFileAsInfected, QuarantineFile}
import uk.gov.hmrc.fileupload.quarantine.FileInQuarantineStored
import uk.gov.hmrc.fileupload.virusscan.ScanningService._
import uk.gov.hmrc.fileupload.{EnvelopeId, Event, FileId, FileRefId}

import scala.collection.immutable.Queue
import scala.concurrent.{ExecutionContext, Future}

class ScannerActor(subscribe: (ActorRef, Class[_]) => Boolean,
                   scanBinaryData: (EnvelopeId, FileId, FileRefId) => Future[ScanResult],
                   commandHandler: CommandHandler)
                  (implicit executionContext: ExecutionContext) extends Actor {

  private var outstandingScans = Queue.empty[Event]
  private var scanningEvent: Option[Event] = None

  override def preStart: Unit = {
    subscribe(self, classOf[QuarantineFile])
    subscribe(self, classOf[VirusScanRequested])
  }

  def receive: PartialFunction[Any, Unit] = {
    case e: QuarantineFile =>
      val fileStored = FileInQuarantineStored(e.id, e.fileId, e.fileRefId, e.created, e.name, e.length, e.contentType, e.metadata)
      outstandingScans = outstandingScans enqueue fileStored
      scanNext()

    case e: VirusScanRequested =>
      outstandingScans = outstandingScans enqueue e
      scanNext()
  }

  def receiveWhenScanning: Receive = {
    case e: QuarantineFile =>
      val fileStored = FileInQuarantineStored(e.id, e.fileId, e.fileRefId, e.created, e.name, e.length, e.contentType, e.metadata)
      outstandingScans = outstandingScans enqueue fileStored

    case e: VirusScanRequested =>
      outstandingScans = outstandingScans enqueue e

    case executed: ScanExecuted =>
      executed.result match {
        case Xor.Right(ScanResultFileClean) =>
          notify(hasVirus = false)
          scanNext()
        case Xor.Left(ScanResultVirusDetected) =>
          notify(hasVirus = true)
          scanNext()
        case Xor.Left(a: ScanError) =>
          Logger.error(s"Scan of file ${executed.requestedFor} failed with ScanError: $a")
          scanNext()
      }
    case e: Failure =>
      Logger.error("Unknown Failure status.", e.cause)
      scanNext()

    case default =>
      Logger.error(s"Unknown message : $default")
      scanNext()
  }

  case class ScanExecuted(requestedFor: FileRefId, result: ScanResult)

  def scanNext(): Unit = {
    outstandingScans.dequeueOption match {
      case Some((e, newQueue)) =>
        context become receiveWhenScanning
        outstandingScans = newQueue
        scanningEvent = Some(e)

        Logger.info(s"Scan $e")
        scanBinaryData(e.envelopeId, e.fileId, e.fileRefId)
          .map(ScanExecuted(e.fileRefId, _)) pipeTo self

      case None =>
        context become receive
        scanningEvent = None
    }
  }

  def notify(hasVirus: Boolean): Unit =
    scanningEvent.foreach { e =>
      val command = if(hasVirus) {
        MarkFileAsInfected(e.envelopeId, e.fileId, e.fileRefId)
      } else {
        MarkFileAsClean(e.envelopeId, e.fileId, e.fileRefId)
      }
      commandHandler.notify(command)
    }
}

object ScannerActor {

  def props(subscribe: (ActorRef, Class[_]) => Boolean,
            scanBinaryData: (EnvelopeId, FileId, FileRefId) => Future[ScanResult],
            commandHandler: CommandHandler)
           (implicit executionContext: ExecutionContext) =
    Props(new ScannerActor(subscribe, scanBinaryData, commandHandler))
}
