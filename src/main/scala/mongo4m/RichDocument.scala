package mongo4m

import io.circe.Decoder
import org.mongodb.scala.Document

import scala.util.Try

class RichDocument(val document: Document) extends AnyVal {

  def as[A: Decoder]: Try[A] = {
    BsonUtil.fromBson(document).flatMap(_.as[A].toTry)
  }
}
