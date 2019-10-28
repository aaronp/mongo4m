package pipelines.users.mongo

import java.time.ZonedDateTime

import pipelines.Schedulers
import pipelines.auth.AuthModel
import pipelines.mongo.{BasePipelinesMongoSpec, CollectionSettings, MongoConnect}

trait RefDataMongoSpec extends BasePipelinesMongoSpec {

  "AuthServiceMongo.update" should {
    "fail if we try to update an old revision" in {
      connect.use { db =>
        Schedulers.using { implicit s =>
          Given("An authentication service")

          val usersCollectionName = s"auth-${System.currentTimeMillis}"
          val settings            = connect.settingsForCollection(usersCollectionName, basedOn = "roles")
          val authService         = RefDataMongo[AuthModel](db, settings).futureValue

          authService.latestModel() shouldBe None
          authService.latestUpdate() shouldBe None
          val firstRecordTime = ZonedDateTime.now()

          When("We create our first record")
          val firstModel = AuthModel(Map("some role" -> Set("do stuff")))
          authService.update(0, "admin user", firstModel, firstRecordTime).futureValue
          val firstVers = eventually {
            val opt = authService.repo.maxVersion.futureValue
            opt should not be (empty)
            opt.get
          }
          firstVers shouldBe 1

          Then("the latest model should be updated")
          eventually {
            authService.latestModel() shouldBe Some(firstModel)
          }
          authService.latestUpdate().map(_.revision) shouldBe Some(1)

          When("we try to update w/ the same revision")
          Then("it should fail")
          val bang = intercept[Exception] {
            authService.update(0, "out of date user", AuthModel(Map("some role" -> Set("out of date"))), firstRecordTime).futureValue
          }
          bang.getMessage should include("dup key")

          When("We try again w/ the latest version")
          val Some((vers, foo)) = authService.latest()
          authService
            .update(vers.revision, "dave the user", foo.copy(permissionsByRole = foo.permissionsByRole.updated("update", Set("perm"))), firstRecordTime.plusHours(1))
            .futureValue

          eventually {
            authService.latestUpdate.map(_.revision) shouldBe Some(2)
          }
        }
      }
    }
  }
}
