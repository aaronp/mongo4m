package mongo4m

import io.circe.Json
import io.circe.literal._
import monix.eval.Task
import monix.execution.CancelableFuture
import monix.reactive.Observable
import monix.reactive.subjects.Var
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.{Completed, Document, MongoCollection}
import org.scalatest.GivenWhenThen

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

trait ReactiveMongoSpec extends BasePipelinesMongoSpec with GivenWhenThen {

  "ReactiveMongo" should {
    "be able to return a stream of query results based on a stream of mongo/query tuples" in {
      Schedulers.use(Schedulers.computeAsync()) { implicit sched =>
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

        Given("An observable of our database collection")
        val collections: Observable[MongoCollection[Document]] = Observable
          .fromResource(connect.mongoDbResource)
          .flatMap { db =>
            // create a new collection from our database
            val task = connect
              .settingsForCollection(s"test.dbquery${System.currentTimeMillis}")
              .ensureCreated(db)
            Observable.fromTask(task)
          }
          .doOnStart { coll =>
            // insert our test records
            Task(insertTestRecords(coll))
          }

        And("An observable of queries we can push through")
        val testCriteria = Var(json"""{ "record.number" : 2 }""")

        val queries: Observable[DbQuery] = {
          def asDbQuery(criteria: Json): Observable[DbQuery] = {
            Observable.fromTry(criteria.as[Bson].toTry).map { c =>
              DbQuery(c)
            }
          }

          testCriteria.flatMap(asDbQuery)
        }

        And("A combined observable of the two")
        val results = collections.flatMap { coll =>
          queries.map(_.exec(coll))
        }

        When("We collect the results collection")
        val seen = ListBuffer[Int]()

        val task: CancelableFuture[Unit] = results.flatten
          .map(BsonUtil.fromBson)
          .flatMap(Observable.fromTry)
          .foreach { json =>
            val numberValue = json.hcursor
              .downField("record")
              .downField("number")
              .as[Int]
              .getOrElse(sys.error("bug"))
            seen += numberValue
          }
        try {
          Then("We should see the first result")
          eventually {
            seen.toList shouldBe List(2)
          }

          When("We update our query text")
          testCriteria := json"""{ "record.number" : 6 }"""

          Then("We should observe some more results")
          eventually {
            seen.toList shouldBe List(2, 6)
          }

          testCriteria := json""" {"record.number":{"$$gte":7}} """
          eventually {
            seen.toList should contain allOf (2, 6, 8, 9, 7, 10)
          }
        } finally {
          task.cancel()
        }
      }
    }
  }
}
