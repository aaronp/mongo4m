package mongo4m

import io.circe.Encoder
import org.bson.BsonValue
import org.mongodb.scala.bson.BsonDocument

class RichEncodable[A](value: A)(implicit encoder: Encoder[A]) {
  def asBson: BsonValue = BsonUtil.asBson(encoder(value))
  def asBsonDoc: BsonDocument = BsonUtil.asDocument(encoder(value))
}
