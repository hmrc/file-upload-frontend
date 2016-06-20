package uk.gov.hmrc.fileupload

import java.util.concurrent.TimeUnit

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.OneServerPerSuite

import scala.concurrent.duration.FiniteDuration

trait WireMockSpec extends BeforeAndAfterAll with BeforeAndAfterEach with ScalaFutures with OneServerPerSuite  {
  override implicit val defaultTimeout = FiniteDuration(100, TimeUnit.SECONDS)

  private val WIREMOCK_PORT = 21212
  private val stubHost = "localhost"

  protected val wiremockBaseUrl: String = s"http://localhost:$WIREMOCK_PORT"
  protected val wireMockServer = new WireMockServer(wireMockConfig().port(WIREMOCK_PORT))

  override def beforeAll() = {
    wireMockServer.stop()
    wireMockServer.start()
    WireMock.configureFor(stubHost, WIREMOCK_PORT)
  }

  override def afterAll() = {
    wireMockServer.stop()
  }

  override def beforeEach() = {
    WireMock.reset()
  }
}
