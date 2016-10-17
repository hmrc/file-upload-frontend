package uk.gov.hmrc.fileupload

import java.io.{BufferedReader, DataOutputStream, IOException, InputStreamReader}
import java.net.ServerSocket

import cats.data.Xor
import org.apache.logging.log4j.LogManager
import uk.gov.hmrc.clamav.ClamAntiVirus._

import scalaz.concurrent.Future

object FakeClam {

  val logger = LogManager.getLogger(FakeClam.getClass)

  case class FakeClamError(cause: Throwable)

  def connect(): Xor[FakeClamError, ServerSocket] = {
    logger.info("Starting fake Clam")
    try {
      val serverSocket = new ServerSocket(0)
      logger.info(s"Fake Clam started on port ${ serverSocket.getLocalPort }")
      waitForClients(serverSocket)
      Xor.Right(serverSocket)
    } catch {
      case e: IOException => Xor.Left(FakeClamError(e))
    }
  }

  def waitForClients(serverSocket: ServerSocket) = Future {
    val socket = serverSocket.accept()
    val outputStream = socket.getOutputStream
    val dataOutputStream = new DataOutputStream(outputStream)
    val in = new BufferedReader(new InputStreamReader(socket.getInputStream))
    handle(in, dataOutputStream)
    dataOutputStream.close()
    outputStream.close()
    socket.close()
  }.start

  def handle(in: BufferedReader, out: DataOutputStream): Unit = {
    val received = new String(Iterator.continually(in.read)
      .takeWhile(_ != 0)
      .map(_.toByte).toArray)

    logger.info(s"Received $received")
    received match {
      case "zINSTREAM" | "" => handle(in, out)
      case _ =>
        logger.info(s"Responding with $okClamAvResponse")
        out.writeBytes(okClamAvResponse)
        out.flush()
    }
  }
}
