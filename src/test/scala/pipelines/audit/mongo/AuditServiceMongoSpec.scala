package pipelines.audit.mongo

import java.time.ZonedDateTime

import io.circe.literal._
import org.mongodb.scala.{Document, MongoCollection}
import pipelines.Schedulers
import pipelines.audit.AuditVersion
import pipelines.mongo.{BasePipelinesMongoSpec, CollectionSettings, MongoConnect}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

trait AuditServiceMongoSpec extends BasePipelinesMongoSpec {

  "AuditServiceMongo.find" should {

    "be able find based on revision, time and user" in {
      Schedulers.using { implicit sched =>
        val collectionName = s"test.audit${System.currentTimeMillis}"

        val connect = MongoConnect(rootConfig)
        val cs = connect.settingsForCollection(collectionName, basedOn = "audit")

        connect.use { db =>

          val coll: MongoCollection[Document] = cs.ensureCreated(db).futureValue
          val service                         = AuditServiceMongo(db, coll)

          val now = ZonedDateTime.now()

          val tail        = service.poorMansTail(10.millis)
          val tailRecords = ListBuffer[AuditVersion]()
          tail.foreach { doc =>
            tailRecords += doc
          }

          // create some data
          service.latest.futureValue shouldBe None
          service.maxVersion.futureValue shouldBe None

          service.audit(1, "someUser", json"""{ "user" : "test" }""", now).futureValue
          service.latest.futureValue.map(_.revision) shouldBe Option(1)
          service.maxVersion.futureValue shouldBe Option(1)
          eventually {
            tailRecords.size shouldBe 1
          }

          service.audit(2, "anotherUser", json"""{ "foo" : "bar" }""", now.plusHours(1)).futureValue
          service.latest.futureValue.map(_.revision) shouldBe Option(2)
          service.maxVersion.futureValue shouldBe Option(2)
          service.minVersion.futureValue shouldBe Option(1)

          service.audit(3, "anotherUser", json"""{ "foo" : "bar2" }""", now.plusHours(2)).futureValue
          service.latest.futureValue.map(_.revision) shouldBe Option(3)
          service.maxVersion.futureValue shouldBe Option(3)
          service.minVersion.futureValue shouldBe Option(1)

          service.forVersion(1).futureValue.map(_.revision) shouldBe Option(1)
          service.forVersion(2).futureValue.map(_.revision) shouldBe Option(2)
          eventually {
            tailRecords.map(_.revision).toList shouldBe List(1, 2, 3)
          }

          try {
            withClue("revision 2 should be found") {
              val found = service.find(revision = Option(2)).toListL.runToFuture.futureValue
              withClue(found.mkString("\n")) {
                val List(second) = found
                second.revision shouldBe 2
                second.userId shouldBe "anotherUser"
                second.createdAt shouldBe now.plusHours(1).toInstant.toEpochMilli
              }
            }

            withClue("revision 4 should not be found") {
              service.find(revision = Option(2)).toListL.runToFuture.futureValue should not be (empty)
            }

            withClue("find revisions after a timestamp") {
              val afterRange       = now.plusMinutes(30)
              val List(two, three) = service.find(after = Option(afterRange)).toListL.runToFuture.futureValue
              two.userId shouldBe "anotherUser"
              two.createdAt shouldBe now.plusHours(1).toInstant.toEpochMilli

              three.userId shouldBe "anotherUser"
              three.createdAt shouldBe now.plusHours(2).toInstant.toEpochMilli
            }

            withClue("find revisions between timestamps") {
              val List(two) = service.find(before = Option(now.plusMinutes(61)), after = Option(now.plusMinutes(30))).toListL.runToFuture.futureValue
              two.userId shouldBe "anotherUser"
              two.createdAt shouldBe now.plusHours(1).toInstant.toEpochMilli
            }
          } finally {
            service.collection.drop().monix.completedL.runToFuture.futureValue
          }
        }
      }
    }
  }
}
