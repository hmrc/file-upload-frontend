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

package uk.gov.hmrc.fileupload.virusscan

import akka.stream.scaladsl.{Source, StreamConverters}
import akka.stream.Materializer
import java.io.InputStream

import cats.data.Xor
import uk.gov.hmrc.clamav.model.{Clean, Infected}
import play.api.libs.iteratee.Iteratee
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.clamav.config.ClamAvConfig
import uk.gov.hmrc.clamav.model.ScanningResult
import uk.gov.hmrc.fileupload.utils.NonFatalWithLogging
import uk.gov.hmrc.fileupload.virusscan.ScanningService._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

class VirusScanner(mkClamAvClient: ClamAvConfig => ClamAvClient, config: Configuration, environment: Environment) {
  private val clamAvClient =
    mkClamAvClient(
      new ClamAvConfig {
        def getString(key: String) =
          config.getString(key).getOrElse(sys.error(s"No config for key `$key` defined"))

        def getInt(key: String) =
          config.getInt(key).getOrElse(sys.error(s"No config for key `$key` defined"))


        override val host   : String = getString(s"${environment.mode}.clam.antivirus.host")
        override val port   : Int    = getInt(s"${environment.mode}.clam.antivirus.port")
        override val timeout: Int    = getInt(s"${environment.mode}.clam.antivirus.timeout")
      }
    )

  private val commandReadTimedOutMessage = "COMMAND READ TIMED OUT"

  def scan(
    source: Source[Array[Byte], akka.NotUsed],
    length: Long
  )(implicit
    ec: ExecutionContext,
    materializer: Materializer
  ): Future[ScanResult] =
    scanWith(clamAvClient.sendAndCheck)(source, length)

  private[virusscan] def scanWith(
    sendAndCheck: (InputStream, Int) => Future[ScanningResult]
  )(
    source: Source[Array[Byte], akka.NotUsed],
    length: Long
  )(implicit
    ec: ExecutionContext,
    materializer: Materializer
  ): Future[ScanResult] = {
    val inputStream: InputStream = source.map(akka.util.ByteString.apply).runWith(StreamConverters.asInputStream(3.seconds))
    sendAndCheck(inputStream, length.toInt).map {
      case Clean             => Xor.right(ScanResultFileClean)
      case Infected(message) if message.contains(commandReadTimedOutMessage) =>
                                Xor.left(ScanReadCommandTimeOut)
      case Infected(message) => Logger.warn(s"File is infected: [$message].")
                                Xor.left(ScanResultVirusDetected)
    }.recover {
      case NonFatalWithLogging(ex) => Xor.left(ScanResultError(ex))
    }
  }
}
