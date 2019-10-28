package pipelines.users.mongo

import org.mongodb.scala.{Document, MongoCollection}
import pipelines.Schedulers
import pipelines.mongo.{BasePipelinesMongoSpec, CollectionSettings, MongoConnect}
import pipelines.users.CreateUserRequest
import pipelines.users.jvm.PasswordHash

trait UserRepoMongoSpec extends BasePipelinesMongoSpec {

  "UserServiceMongo.find" should {
    "find users by email or password" in {
      connect.use { db =>
        Schedulers.using { implicit s =>
          val usersCollectionName = s"users-${System.currentTimeMillis}"
          val settings            = connect.settingsForCollection(usersCollectionName, "users")

          val coll: MongoCollection[Document] = settings.ensureCreated(db).futureValue
          val userService                     = UserRepoMongo(db, coll, PasswordHash(rootConfig))

          try {

            Given("A new user 'dave'")
            val daveReq = CreateUserRequest("dave", s"d@ve.com", "password")
            // this should succeed:
            userService.createUser(daveReq).futureValue

            val Some(foundByName)  = userService.findUser("dave").futureValue
            val Some(foundByEmail) = userService.findUser("d@ve.com").futureValue
            foundByName shouldBe foundByEmail
            foundByEmail.id.length should be > 5
            foundByName.id shouldBe foundByEmail.id
            foundByName.id should not be (empty)
          } finally {
            userService.users.drop().monix.completedL.runToFuture.futureValue
          }
        }
      }
    }
  }
  "UserServiceMongo.createUser" should {
    "not be able to create a user w/ the same name or email" in {
      connect.use { db =>
        Schedulers.using { implicit s =>
          val usersCollectionName = s"users-${System.currentTimeMillis}"
          val settings            = connect.settingsForCollection(usersCollectionName, "users").ensureCreated(db).futureValue
          val userService         = UserRepoMongo(db, settings, PasswordHash(rootConfig))

          try {

            Given("A new user 'dave'")
            val daveReq = CreateUserRequest("dave", s"d@ve.com", "password")
            // this should succeed:
            userService.createUser(daveReq).futureValue

            When("We try to create him a second time")
            Then("It should fail")

            val Left(error) = userService.createUser(daveReq).futureValue
            error.message shouldBe "That user already exists"

            And("It should fail if just the email is different")
            val Left(bang2) = userService.createUser(daveReq.copy(email = "changed" + daveReq.email)).futureValue
            bang2.message shouldBe "That user already exists"

            And("It should fail if just the name is different")
            val Left(bang3) = userService.createUser(daveReq.copy(userName = "changed" + daveReq.userName)).futureValue
            bang3.message shouldBe "That user already exists"

          } finally {
            userService.users.drop().monix.completedL.runToFuture.futureValue
          }
        }
      }
    }
  }
}
