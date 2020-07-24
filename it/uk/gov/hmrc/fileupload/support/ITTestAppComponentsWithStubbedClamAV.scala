package uk.gov.hmrc.fileupload.support

import play.api.{Configuration, Environment}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterEach, Suite}
import uk.gov.hmrc.fileupload.virusscan.AvClient

trait ITTestAppComponentsWithStubbedClamAV extends IntegrationTestApplicationComponents with BeforeAndAfterEach with MockFactory {
  this: Suite =>

  protected val stubbedAvClient: AvClient = stub[AvClient]

  override lazy val disableAvScanning: Boolean = false
  override lazy val numberOfTimeoutAttempts: Int = 3
  override lazy val mkAvClient: ((Configuration, Environment)) => AvClient =
    _ => stubbedAvClient
}
