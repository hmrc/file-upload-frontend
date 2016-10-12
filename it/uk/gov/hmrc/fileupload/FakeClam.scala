package uk.gov.hmrc.fileupload

import java.io.{BufferedReader, DataOutputStream, InputStreamReader}
import java.net.ServerSocket

import play.api.Logger
import uk.gov.hmrc.clamav.config.ClamAvConfig._

import scalaz.concurrent.Future

object FakeClam {

  def connect() = {
    val serverSocket = new ServerSocket(0)
    Logger.info(s"Fake clam started on port ${serverSocket.getLocalPort}")

    Future {
      val socket = serverSocket.accept()
      val out = new DataOutputStream(socket.getOutputStream)
      val in = new BufferedReader(new InputStreamReader(socket.getInputStream))
      handle(in, out)
      socket.close()
      serverSocket.close()
    }.start
    serverSocket
  }


  def handle(in: BufferedReader, out: DataOutputStream): Unit = {
    val received = new String(Iterator.continually(in.read)
      .takeWhile(a => a != 0)
      .map(_.toByte).toArray)

    Logger.info(s"Received $received")
    received match {
      case "zINSTREAM" | "" => handle(in, out)
      case _ =>
        Logger.info(s"Responding with $okClamAvResponse")
        out.writeBytes(okClamAvResponse)
        out.flush()
    }
  }
}
