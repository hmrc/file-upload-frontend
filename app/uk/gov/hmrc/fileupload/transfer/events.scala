package uk.gov.hmrc.fileupload.transfer

import uk.gov.hmrc.fileupload.{EnvelopeId, FileId}

case class ToTransientMoved(envelopeId: EnvelopeId, fileId: FileId)

case class MovingToTransientFailed(envelopeId: EnvelopeId, fileId: FileId, reason: String)