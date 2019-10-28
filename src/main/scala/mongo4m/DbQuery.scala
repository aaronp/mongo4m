package mongo4m

import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}
import monix.reactive.Observable
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Sorts
import org.mongodb.scala.{Document, MongoCollection}

final case class DbQuery(query: Option[Bson],
                         projection: Option[Bson],
                         sortFields: Seq[String],
                         ascending: Boolean = true) {

  /**
    * Execute this query on the given mongo collection
    * @param coll
    * @return the results as an observable
    */
  def exec(coll: MongoCollection[Document]): Observable[Document] = {
    val findObservable = coll.find()
    projection.foreach(findObservable.projection)
    query.foreach(findObservable.filter)

    if (sortFields.nonEmpty) {
      val sort = if (ascending) {
        Sorts.ascending(sortFields: _*)
      } else {
        Sorts.descending(sortFields: _*)
      }
      findObservable.sort(sort)
    }

    import LowPriorityMongoImplicits._
    findObservable.monix
  }
}

object DbQuery {

  def apply(query: Bson): DbQuery =
    new DbQuery(query = Option(query), None, Nil)

  implicit object encoder extends Encoder[DbQuery] {
    override def apply(dbQuery: DbQuery): Json = {
      import io.circe.syntax._

      implicit val bsonEncoder = BsonUtil.codec.encoder

      val queryAsJson = dbQuery.query.map(_.asJson)
      val projAsJson = dbQuery.projection.map(_.asJson)

      Json.obj(
        "query" -> Json.arr(queryAsJson.toSeq: _*),
        "projection" -> Json.arr(projAsJson.toSeq: _*),
        "sortFields" -> Json.arr(dbQuery.sortFields.map(_.asJson): _*),
        "ascending" -> Json.fromBoolean(dbQuery.ascending)
      )
    }
  }
  implicit object decoder extends Decoder[DbQuery] {
    implicit val bsonDec = BsonUtil.codec.decoder
    override def apply(c: HCursor): Result[DbQuery] = {
      for {
        query <- c.downField("query").downArray.as[Option[Bson]]
        projection <- c.downField("projection").downArray.as[Option[Bson]]
        sortFields <- c.downField("sortFields").as[Seq[String]]
        ascending <- c.downField("ascending").as[Boolean]
      } yield {
        DbQuery(query, projection, sortFields, ascending)
      }
    }
  }

}
