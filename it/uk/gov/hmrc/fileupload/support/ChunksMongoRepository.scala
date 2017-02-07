package uk.gov.hmrc.fileupload.support

import play.api.libs.json.Json
import reactivemongo.api.{DB, DBMetaCommands}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository

case class Chunk(files_id: String)

object Chunk {
  implicit val format = Json.format[Chunk]
}

class ChunksMongoRepository(mongo: () => DB with DBMetaCommands)
    extends ReactiveRepository[Chunk, BSONObjectID](collectionName = "quarantine.chunks", mongo, domainFormat = Chunk.format) {
}
