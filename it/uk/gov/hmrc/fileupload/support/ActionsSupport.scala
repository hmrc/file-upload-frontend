package uk.gov.hmrc.fileupload.support

import org.scalatest.Suite
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.http.Status

import scala.concurrent.ExecutionContext

trait ActionsSupport extends ScalaFutures with Status with IntegrationTestApplicationComponents {
  this: Suite =>

  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(5, Millis))

  val url = "http://localhost:9000/file-upload"
  implicit val ec = ExecutionContext.global
  val client = components.wsClient
}
