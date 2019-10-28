package pipelines.users.mongo

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import monix.execution.{CancelableFuture, Scheduler}
import org.mongodb.scala.MongoDatabase
import pipelines.auth.{AuthModel, SetRolesForUserRequest, UserRoles}
import pipelines.core.{GenericErrorResult, GenericMessageResult}
import pipelines.mongo.CollectionSettings

import scala.concurrent.{ExecutionContext, Future}

/**
  * Contains the info for logging in/updating users, as well as their roles
  */
case class UserRolesService(authRepo: RefDataMongo[AuthModel], userRoleRepo: RefDataMongo[UserRoles]) extends StrictLogging {
  private[users] def authCollection      = authRepo.repo.collection
  private[users] def userRolesCollection = userRoleRepo.repo.collection

  def updateUserRoles(updatingUser: String, request: SetRolesForUserRequest)(
      implicit executionContext: ExecutionContext): Future[Either[GenericErrorResult, GenericMessageResult]] = {
    logger.info(s"'$updatingUser' is updating userRoles: ${request}")
    userRoleRepo.latest() match {
      case Some((version, value)) if version.revision == request.version =>
        // this check is just a convenience - our index should error if an update is made for the same revision anyway
        val newValue = value.copy(rolesByUserId = value.rolesByUserId.updated(request.userId, request.roles))
        userRoleRepo.update(request.version, updatingUser, newValue).map { _ =>
          Right(GenericMessageResult(s"Roles for user '${request.userId}' updated"))
        }
      case Some((version, _)) =>
        val errorMsg = s"An attempt was made to update out-of-date user roles for version ${request.version} " +
          s"as the latest version is ${version.version}. Couldn't update roles for user '${request.userId}'"
        Future.successful(Left(GenericErrorResult(errorMsg)))
      case None =>
        //
        // no data -- our first!
        //
        logger.warn(s"Setting the FIRST userRoles for ${request.userId} from ${updatingUser}")
        val newValue = UserRoles(Map(request.userId -> request.roles))
        userRoleRepo.update(0, updatingUser, newValue).map { _ =>
          Right(GenericMessageResult(s"Roles for user '${request.userId}' updated"))
        }
    }
  }

  private val nowt = Set.empty[String]
  def permissionsForRole(roleId: String): Set[String] = {
    authRepo.latestModel().fold(nowt) { model =>
      model.permissionsForRole(roleId)
    }
  }

  def rolesForUser(userId: String): Set[String] = userRoleRepo.latestModel().fold(nowt)(_.rolesByUserId.getOrElse(userId, nowt))

  def permissionsForUser(userId: String): Set[String] = {
    val authModel: Option[AuthModel] = authRepo.latestModel()
    authModel.fold(nowt) { model =>
      rolesForUser(userId).flatMap(model.permissionsForRole)
    }
  }
}

object UserRolesService {
  def apply(mongo: MongoDatabase, rootConfig: Config)(implicit ioScheduler: Scheduler): CancelableFuture[UserRolesService] = {
    val userRoles: CollectionSettings = CollectionSettings(rootConfig, "userRoles")
    val roles                         = CollectionSettings(rootConfig, "roles")
    apply(mongo, userRoles, roles)
  }

  def apply(mongo: MongoDatabase, users: CollectionSettings, roles: CollectionSettings)(implicit ioScheduler: Scheduler): CancelableFuture[UserRolesService] = {
    val first  = RefDataMongo[AuthModel](mongo, roles)
    val second = RefDataMongo[UserRoles](mongo, users)
    for {
      authService <- first
      userService <- second
    } yield {
      new UserRolesService(authService, userService)
    }
  }
}
