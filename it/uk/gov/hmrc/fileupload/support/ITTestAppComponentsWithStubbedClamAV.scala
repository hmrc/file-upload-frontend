package uk.gov.hmrc.fileupload.support

import org.scalatest.{BeforeAndAfterEach, Suite}
import org.mockito.MockitoSugar
import uk.gov.hmrc.fileupload.virusscan.AvClient

trait ITTestAppComponentsWithStubbedClamAV
  extends IntegrationTestApplicationComponents
     with BeforeAndAfterEach
     with MockitoSugar {
  this: Suite =>

  lazy val stubbedAvClient: AvClient = mock[AvClient]

  override lazy val disableAvScanning: Boolean = false
  override lazy val numberOfTimeoutAttempts: Int = 3
  override lazy val avClient: Option[AvClient] = Some(stubbedAvClient)

  override def beforeEach {
    super.beforeEach()
    reset(stubbedAvClient)
  }
}
