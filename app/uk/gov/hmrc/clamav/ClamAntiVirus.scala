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

package uk.gov.hmrc.clamav

import org.apache.commons.io.IOUtils
import uk.gov.hmrc.clamav.config.ClamAvConfig
import uk.gov.hmrc.clamav.model._

import java.io.{ByteArrayInputStream, InputStream}
import scala.concurrent.{ExecutionContext, Future}

class ClamAntiVirus private[clamav] (clamAvConfig: ClamAvConfig)(using ExecutionContext) {

  private val Handshake              = "zINSTREAM\u0000"
  private val FileCleanResponse      = "stream: OK\u0000"
  private val VirusFoundResponse     = "stream\\: (.+) FOUND\u0000".r
  private val ParseableErrorResponse = "(.+) ERROR\u0000".r

  def sendAndCheck(
    inputStream: InputStream,
    length: Int
  )(using ExecutionContext): Future[ScanningResult] =
    if length > 0 then
      ClamAvSocket.withSocket(clamAvConfig): connection =>
        for
          _              <- sendHandshake(connection)
          _              <- sendRequest(connection)(inputStream, length)
          response       <- readResponse(connection)
          parsedResponse <- parseResponse(response)
        yield parsedResponse
    else
      Future.successful(ScanningResult.Clean)

  def sendAndCheck(bytes: Array[Byte])(using ExecutionContext): Future[ScanningResult] =
    sendAndCheck(ByteArrayInputStream(bytes), bytes.length)

  private def sendHandshake(connection: Connection)(using ExecutionContext) =
    Future:
      connection.out.write(Handshake.getBytes)

  private def sendRequest(connection: Connection)(stream: InputStream, length: Int)(using ExecutionContext) =
    Future:
      connection.out.writeInt(length)
      IOUtils.copy(stream, connection.out)
      connection.out.writeInt(0)
      connection.out.flush()

  private def readResponse(connection: Connection): Future[String] =
    Future:
      IOUtils.toString(connection.in, "UTF-8")

  private def parseResponse(response: String) =
    response match
      case FileCleanResponse             => Future.successful(ScanningResult.Clean)
      case VirusFoundResponse(virus)     => Future.successful(ScanningResult.Infected(virus))
      case ParseableErrorResponse(error) => Future.failed(ClamAvException(error))
      case unparseableResponse =>
        Future.failed(ClamAvException(s"Unparseable response from ClamAV: $unparseableResponse"))
}
