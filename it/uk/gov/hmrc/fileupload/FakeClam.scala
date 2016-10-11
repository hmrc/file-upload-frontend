package uk.gov.hmrc.fileupload

import java.io.{BufferedReader, DataOutputStream, InputStreamReader}
import java.net.ServerSocket

import play.api.Logger
import uk.gov.hmrc.clamav.config.ClamAvConfig

import scalaz.concurrent.Future

object FakeClam {

  def connect() = {
    val serverSocket = new ServerSocket(0)
    Logger.warn(s"Fake clam started on port ${serverSocket.getLocalPort}")

    Future {
      val socket = serverSocket.accept()
      val out = new DataOutputStream(socket.getOutputStream)
      val in = new BufferedReader(new InputStreamReader(socket.getInputStream))
      Iterator.continually(in.read()).takeWhile(_ != 0).toList
      out.writeBytes(ClamAvConfig.okClamAvResponse)
      out.flush()
      socket.close()
      serverSocket.close()
    }.start
    serverSocket
  }
}
