package uk.gov.hmrc.fileupload.support

import org.scalatest.Suite
import play.api.libs.ws.WSClient


trait ActionsSupport extends IntegrationTestApplicationComponents {
  this: Suite =>

  val url = "http://localhost:9000/file-upload"
  val internalUrl = "http://localhost:9000/internal-file-upload"
  val client: WSClient = components.wsClient
}
