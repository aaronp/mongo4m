package mongo4m

import java.util.UUID

import io.circe.Json
import io.circe.literal._
import org.mongodb.scala.bson.collection.immutable.Document

import scala.util.Success

trait MongoConnectSpec extends BasePipelinesMongoSpec {

  "MongoConnect" should {
    "connect" in {
      Schedulers.using { implicit scheduler =>
        val collection =
          mongoDb.getCollection(s"coll_${UUID.randomUUID()}".filter(_.isLetter))

        val doc: Json = json"""{
                "userName" : "name",
                "email" : "n@me.com",
                "age" : 123
                }"""

        // insert summat
        val List(_) = collection
          .insertOne(BsonUtil.asDocument(doc))
          .monix
          .toListL
          .runSyncUnsafe(testTimeout)

        val query = json"""{ "userName" : "name" } """
        val List(found) = collection
          .find[Document](BsonUtil.asDocument(query))
          .monix
          .toListL
          .runSyncUnsafe(testTimeout)
        val Success(written) = found.as[Json]

        val readBackMap = written.asObject.get.toMap
        readBackMap.contains("_id") shouldBe true
        readBackMap("email").asString shouldBe Some("n@me.com")
      }
    }
  }
}
