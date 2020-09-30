package uk.gov.hmrc.fileupload.support

import org.scalatest.Suite
import play.api.libs.ws.WSClient


trait ActionsSupport extends IntegrationTestApplicationComponents {
  this: Suite =>

  val url = s"http://localhost:$port/file-upload"
  val internalUrl = s"http://localhost:$port/internal-file-upload"
  val wsClient = app.injector.instanceOf[WSClient]
}
