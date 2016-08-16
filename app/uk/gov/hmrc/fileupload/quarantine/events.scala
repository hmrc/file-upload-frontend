package uk.gov.hmrc.fileupload.quarantine

import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

case class Quarantined(envelopeId: EnvelopeId, fileId: FileId)
