package uk.gov.hmrc.fileupload.virusscan

import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

case class NoVirusDetected(envelopeId: EnvelopeId, fileId: FileId)

case class VirusDetected(envelopeId: EnvelopeId, fileId: FileId, reason: String)