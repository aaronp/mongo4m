package pipelines.users.mongo

import com.typesafe.config.Config
import monix.execution.Scheduler
import org.mongodb.scala.MongoDatabase
import pipelines.mongo.MongoConnect
import pipelines.users.{Claims, LoginHandler, LoginRequest}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
  * Reads the users and auth from
  *
  * @param users
  * @param userRoles
  * @param sessionDuration
  */
class LoginHandlerMongo(val users: UserRepoMongo, val userRoles: UserRolesService, sessionDuration: FiniteDuration) extends LoginHandler[Future] {

  override def login(request: LoginRequest): Future[Option[Claims]] = {
    implicit val ec = users.ioSched
    users.findUser(request.user).map {
      case Some(found) =>
        if (found.hashedPassword == users.hasher(request.password)) {
          val roles: Set[String] = userRoles.rolesForUser(found.userName)
          val perms: Set[String] = roles.flatMap(userRoles.permissionsForRole)
          val claims = Claims
            .after(sessionDuration)
            .forUser(found.userName) //
            .withId(found.id) //
            .setRoles(roles) //
            .setPermissions(perms) //
            .copy(email = found.email) //
          Some(claims)
        } else {
          None
        }
      case None => None
    }
  }
}

object LoginHandlerMongo {
  def apply(mongo: MongoDatabase, rootConfig: Config)(implicit ioSched: Scheduler): Future[LoginHandlerMongo] = {
    import args4c.implicits._
    val sessionDuration                      = rootConfig.asFiniteDuration("pipelines.users.sessionDuration")
    val usersFuture: Future[UserRepoMongo]   = UserRepoMongo(mongo, rootConfig)
    val authFuture: Future[UserRolesService] = UserRolesService(mongo, rootConfig)
    for {
      u <- usersFuture
      a <- authFuture
    } yield {
      new LoginHandlerMongo(u, a, sessionDuration)
    }
  }

//  def apply(rootConfig: Config)(implicit ioSched: Scheduler): Future[MongoDatabase] = {
//
//    val conn = MongoConnect(rootConfig)
//
//    val c = conn.client
//    c -> c.getDatabase(database)
//  }
}
