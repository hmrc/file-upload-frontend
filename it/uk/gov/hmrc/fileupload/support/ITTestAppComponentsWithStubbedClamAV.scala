package uk.gov.hmrc.fileupload.support

import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterEach, Suite}
import uk.gov.hmrc.clamav.config.ClamAvConfig
import uk.gov.hmrc.fileupload.virusscan.ClamAvClient

trait ITTestAppComponentsWithStubbedClamAV extends IntegrationTestApplicationComponents with BeforeAndAfterEach with MockFactory {
  this: Suite =>

  protected val stubbedClamAVClient: ClamAvClient = stub[ClamAvClient]

  override lazy val disableAvScanning: Boolean = false
  override lazy val numberOfTimeoutAttempts: Int = 3
  override lazy val mkClamAvClient: ClamAvConfig => ClamAvClient = _ => stubbedClamAVClient
}
