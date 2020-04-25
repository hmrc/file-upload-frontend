package uk.gov.hmrc.fileupload.support

import org.scalatest.{BeforeAndAfterEach, Suite}
import uk.gov.hmrc.clamav.{ClamAntiVirus, VirusDetectedException}
import uk.gov.hmrc.clamav.config.ClamAvConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait StubClamAntiVirus extends IntegrationTestApplicationComponents with BeforeAndAfterEach {
  this: Suite =>

  var numberOfScansAttempted: Int = 0
  var numberOfVirusDetectedToReturn: Int = 0
  var virusDetectedMessage: String = ""
  var sendResult: Future[Unit] = Future.successful(())

  override lazy val disableAvScanning: Boolean = false
  override lazy val numberOfTimeoutAttempts: Int = 3

  override lazy val clamAntiVirusTestClient: ClamAvConfig => ClamAntiVirus = config => new ClamAntiVirus(config) {
    override def send(bytes: Array[Byte])(implicit ec: ExecutionContext): Future[Unit] = sendResult

    override def checkForVirus()(implicit ec: ExecutionContext): Future[Try[Boolean]] = {
      numberOfScansAttempted = numberOfScansAttempted + 1
      if (numberOfScansAttempted > numberOfVirusDetectedToReturn) Future.successful(Success(true))
      else {
        Future.successful(Failure(new VirusDetectedException(virusDetectedMessage)))
      }
    }
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    numberOfScansAttempted = 0
    numberOfVirusDetectedToReturn = 0
    sendResult = Future.successful(())
    virusDetectedMessage = ""
  }

  def setVirusDetected(virusMessage: String, timesToReturnVirus: Int) = {
    virusDetectedMessage = virusMessage
    numberOfVirusDetectedToReturn = timesToReturnVirus
  }
}
