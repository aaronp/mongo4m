package pipelines.users.mongo

import pipelines.Schedulers
import pipelines.mongo.{BasePipelinesMongoSpec, CollectionSettings, MongoConnect}
import pipelines.users.jvm.PasswordHash
import pipelines.users.{CreateUserRequest, LoginRequest}

import scala.concurrent.duration._

trait LoginHandlerMongoSpec extends BasePipelinesMongoSpec {

  "LoginHandlerMongo" should {
    "be able to log in a newly created user" in {
      val connect = MongoConnect(rootConfig)
      connect.use { db =>
        Schedulers.using { implicit sched =>
          val authService: UserRolesService = {
            val usersCollectionName = s"users-${System.currentTimeMillis}"
            val rolesCollectionName = s"roles-${System.currentTimeMillis}"

            val rolesSettings: CollectionSettings = connect.settingsForCollection(rolesCollectionName, basedOn = "roles")
            val userSettings  = connect.settingsForCollection(usersCollectionName, basedOn = "userRoles")

            UserRolesService(db, userSettings, rolesSettings).futureValue
          }

          val users = {
            val usersCollectionName = s"users-${System.currentTimeMillis}"
            val config              = connect.configForCollection(usersCollectionName)
            val settings            = connect.settingsForCollection(usersCollectionName, "users")
            val coll                = settings.ensureCreated(mongoDb).futureValue
            UserRepoMongo(db, coll, PasswordHash(rootConfig))
          }

          try {

            val underTest      = new LoginHandlerMongo(users, authService, 10.minutes)
            val userEnteredPwd = "password"
            users.createUser(CreateUserRequest("under", "t@st.com", userEnteredPwd)).futureValue

            val Some(byUserName) = underTest.login(LoginRequest("under", userEnteredPwd)).futureValue
            byUserName.email shouldBe "t@st.com"

            val Some(byEmailName) = underTest.login(LoginRequest("t@st.com", userEnteredPwd)).futureValue
            byEmailName.email shouldBe "t@st.com"

            byUserName.name shouldBe byEmailName.name

            underTest.login(LoginRequest("t@st.com", "wrong")).futureValue shouldBe None

          } finally {
            authService.authCollection.drop()
            authService.userRolesCollection.drop()
          }
        }
      }

    }
  }
}
