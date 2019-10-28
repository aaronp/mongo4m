package mongo4m

import cats.effect.IO
import io.circe.{Decoder, ObjectEncoder}
import io.circe.literal._

import monix.execution.ExecutionModel
import monix.reactive.Observable
import monix.reactive.subjects.Var
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.{Filters, Projections}
import org.mongodb.scala.{Completed, Document, MongoCollection}
import org.scalatest.GivenWhenThen

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

trait DbQueryTest extends BasePipelinesMongoSpec with GivenWhenThen {

  "DbQuery.exec" should {
    "apply the query, projection and sort criteria" in {
      Schedulers.use(Schedulers.compute()) { implicit sched =>
        Given("Some records in a new mongo collection")
        val records = Seq(
          json"""{ "record" : {
                    "a" : true,
                    "number" : 1234
                  } }""",
          json"""{ "record" : {
                    "a" : true,
                    "number" : 12,
                    "letter" : "a"
                  } }""",
          json"""{ "record" : {
                    "a" : false,
                    "number" : 9876
                  } }"""
        )

        import io.circe.syntax._
        val query = new DbQuery(
          query = Option(Filters.eq("record.a", true)),
          projection = Option(Projections.include("record.number")),
          sortFields = Seq("record.y"),
          ascending = false
        ).asJson
          .as[DbQuery]
          .toTry
          .get // let's use a query marshalled/unmarshalled from json, just to prove our codecs work

        val populateNewCollection = connect.mongoDbResource.use { db =>
          IO {
            val collection = connect
              .settingsForCollection(s"test.dbquery${System.currentTimeMillis}")
              .ensureCreated(db)
              .futureValue
            val futures: Seq[Future[Completed]] = records.map { r =>
              collection.insertOne(r).headL.runToFuture
            }
            Future.sequence(futures).futureValue

            When("We execute a query using a filter, project and sort field")
            query.exec(collection).toListL.runToFuture.futureValue
          }
        }
        val found = populateNewCollection.unsafeToFuture().futureValue

        Then("We should find our projected records")
        found.size shouldBe 2

        // the values contain the id and the record ... we just want the record
        val recordsFound =
          found.map(_.values.last).map(BsonUtil.bsonValueAsJson)
        recordsFound should contain only (
          json"""{ "number" : 1234}""",
          json"""{ "number" : 12}"""
        )
      }
    }
  }
  "DbQuery" should {
    "marshal to and from json" in {

      val originalFilter: Bson =
        Filters.or(Filters.eq("foo", 2), Filters.eq("bar", 3))
      val query = new DbQuery(
        query = Option(originalFilter),
        projection = Option(Projections.include("foo", "bar")),
        sortFields = Seq("meh", "x.y"),
        ascending = false
      )
      import io.circe.syntax._
      val json = query.asJson
      val Right(backAgain) = json.as[DbQuery]

      backAgain.query.get
    }
    "be able to return a stream of query results based on a stream of mongo/query tuples" in {
      Schedulers.use(
        Schedulers.compute(
          executionModel = ExecutionModel.AlwaysAsyncExecution)) {
        implicit sched =>
          val collectionName = s"test.dbquery${System.currentTimeMillis}"

          val queries = Var[DbQuery](null: DbQuery)

          val collection: Observable[MongoCollection[Document]] =
            connect.collectionObservable(collectionName)

          val both = collection.combineLatest(queries)
          val results: Observable[Document] = both.flatMap {
            case (coll, query) =>
              logger.info(s"\tQuerying $query against $coll")
              query.exec(coll)
          }

          // write some data to the collection
          val head = collection.headL.runToFuture.futureValue

          Observable(1, 2, 3, 4)
            .map(DbQueryTest.Record.apply)
            .subscribe(head.asObserver[DbQueryTest.Record])

          // read all the results
          val received = ListBuffer[Document]()
          val future = results.foreach { doc =>
            println(s"Got result:\n$doc\n")
            received += doc
          }

          // start sending some queries through
          queries := DbQuery(Filters.eq("foo", 2))

          //
          future.onComplete {
            case r =>
              println("Results done with " + r)
          }
      }
    }
  }
}

object DbQueryTest {
  case class Record(foo: Int)
  object Record {
    implicit val encoder: ObjectEncoder[Record] =
      io.circe.generic.semiauto.deriveEncoder[Record]
    implicit val decoder: Decoder[Record] =
      io.circe.generic.semiauto.deriveDecoder[Record]
  }
}
