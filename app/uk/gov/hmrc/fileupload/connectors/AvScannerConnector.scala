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

package uk.gov.hmrc.fileupload.connectors

import play.api.libs.iteratee.{Enumerator, Iteratee}
import uk.gov.hmrc.clamav.VirusChecker

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait AvScannerConnector {
  self: VirusChecker =>

  type ByteIteratee = Iteratee[Array[Byte], Future[Unit]]
  type ByteEnumerator = Enumerator[Array[Byte]]

  import scala.concurrent.ExecutionContext.Implicits.global

  def iteratee = Iteratee.fold(Future(())) { (state, bytes: Array[Byte]) => state flatMap { _ => send(bytes) } }

  def ok(x: Unit) = Success(true)
  def failure: PartialFunction[Throwable, Failure[Boolean]] = { case e => Failure(e) }

  def scan(enumerator: ByteEnumerator) = {
    for {
      unit <- sendData(enumerator, iteratee)
      result <- checkResponse()
    } yield result
  }

  def sendData(enumerator: ByteEnumerator, iteratee: ByteIteratee) = (enumerator |>>> iteratee) flatMap identity

  def checkResponse() = checkForVirus() map ok recover failure
}
