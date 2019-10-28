package mongo4m

import io.circe.Json
import io.circe.literal._
import monix.eval.Task
import monix.reactive.Observable
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters
import org.mongodb.scala.{Completed, Document, MongoCollection}

import io.circe.syntax._

import scala.concurrent.Future

trait ReactiveMongoSpec extends BasePipelinesMongoSpec {

  "ReactiveMongo" should {
    "be able to return a stream of query results based on a stream of mongo/query tuples" in {
      Schedulers.using { implicit sched =>

        implicit val bsonEncoder = BsonUtil.codec.encoder
        implicit val bsonDecoder = BsonUtil.codec.decoder


        def insertTestRecords(collection: MongoCollection[Document]) = {
          val records = (0 to 10).map { i =>
            json"""{ "record" : {
                    "number" : $i
                  } }"""
          }
          val futures: Seq[Future[Completed]] = records.map { r =>
            collection.insertOne(r).headL.runToFuture
          }
          Future.sequence(futures).futureValue
        }


        /**
         * Collection which inserts test records
         */
        val collections: Observable[MongoCollection[Document]] = {
          Observable.fromResource(connect.mongoDbResource).flatMap { db =>
            // create a new collection from our database
            val future = connect
              .settingsForCollection(s"test.dbquery${System.currentTimeMillis}")
              .ensureCreated(db)
            Observable.fromFuture(future)
          }.doOnStart { coll =>
            // insert our test records
            Task(insertTestRecords(coll))
          }
        }

        val eqJson: Json = Filters.eq("record.number", 2).asJson
        println()
        println(eqJson.spaces2)
        println()

        val testCriterias = Observable(
          Filters.eq("record.number", 2).asJson,
          Filters.eq("record.number", 1).asJson,
          Filters.gt("record.number", 7).asJson
        )

        def asDbQuery(criteria: Json): Observable[DbQuery] = {
          Observable.fromTry(criteria.as[Bson].toTry).map { c =>
            DbQuery(c)
          }
        }

        val queries = testCriterias.flatMap(asDbQuery)

        val results = collections.dump("collections").combineLatest(queries).dump("both").flatMap {
          case (coll, query) => query.exec(coll)
        }
        println("- " * 80)

        results.map(BsonUtil.fromBson).map(_.map(_.spaces4)).flatMap(Observable.fromTry).foreach(println)
      }
    }
  }
}
