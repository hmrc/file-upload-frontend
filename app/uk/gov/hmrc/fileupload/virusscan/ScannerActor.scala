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

package uk.gov.hmrc.fileupload.virusscan

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.pipe
import cats.data.Xor
import play.api.Logger
import uk.gov.hmrc.fileupload.FileRefId
import uk.gov.hmrc.fileupload.notifier.NotifierService.NotifyResult
import uk.gov.hmrc.fileupload.quarantine.FileInQuarantineStored
import uk.gov.hmrc.fileupload.virusscan.ScanningService._

import scala.collection.immutable.Queue
import scala.concurrent.{ExecutionContext, Future}

class ScannerActor(subscribe: (ActorRef, Class[_]) => Boolean,
                   scanBinaryData: (FileRefId) => Future[ScanResult],
                   notify: (AnyRef) => Future[NotifyResult])
                  (implicit executionContext: ExecutionContext) extends Actor {

  var outstandingScans = Queue.empty[FileInQuarantineStored]
  var scanningEvent: Option[FileInQuarantineStored] = None

  override def preStart = {
    subscribe(self, classOf[FileInQuarantineStored])
  }

  def receive = {
    case e: FileInQuarantineStored =>
      outstandingScans = outstandingScans enqueue e
      scanNext()
    case _ =>
  }

  def receiveWhenScanning: Receive = {
    case e: FileInQuarantineStored =>
      outstandingScans = outstandingScans enqueue e

    case Xor.Right(ScanResultFileClean) =>
      notify(hasVirus = false)
      scanNext()

    case Xor.Left(ScanResultVirusDetected) =>
      notify(hasVirus = true)
      scanNext()

    case Xor.Left(_) =>
      scanNext()

    case e: Failure =>
      scanNext()

    case _ =>
      scanNext()
  }

  def scanNext(): Unit = {
    outstandingScans.dequeueOption match {
      case Some((e, newQueue)) =>
        context become receiveWhenScanning
        outstandingScans = newQueue
        scanningEvent = Some(e)

        Logger.info(s"Scan ${e.envelopeId} ${e.fileId} ${e.fileRefId}")
        scanBinaryData(e.fileRefId) pipeTo self

      case None =>
        context become receive
        scanningEvent = None
    }
  }

  def notify(hasVirus: Boolean): Unit =
    scanningEvent.foreach { case e =>
      notify(FileScanned(e.envelopeId, e.fileId, e.fileRefId, hasVirus = hasVirus))
    }
}

object ScannerActor {

  def props(subscribe: (ActorRef, Class[_]) => Boolean, scanBinaryData: (FileRefId) => Future[ScanResult], notify: (AnyRef) => Future[NotifyResult])
           (implicit executionContext: ExecutionContext) =
    Props(new ScannerActor(subscribe = subscribe, scanBinaryData = scanBinaryData, notify = notify))
}
