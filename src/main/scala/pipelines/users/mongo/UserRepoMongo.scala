package pipelines.users.mongo

import com.mongodb.ErrorCategory
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import monix.execution.{CancelableFuture, Scheduler}
import org.mongodb.scala.model.Filters
import org.mongodb.scala.{Completed, Document, MongoCollection, MongoDatabase, MongoWriteException, SingleObservable}
import pipelines.core.{GenericErrorResult, GenericMessageResult}
import pipelines.mongo.{BsonUtil, CollectionSettings, LowPriorityMongoImplicits}
import pipelines.users.jvm.PasswordHash
import pipelines.users.{CreateUserRequest, RegisteredUser}

import scala.concurrent.Future
import scala.util.control.NonFatal

/** exposes the means for creating new users
  *
  * @param mongoDb
  * @param users
  * @param hasher
  * @param ioSched
  */
final class UserRepoMongo(private[users] val mongoDb: MongoDatabase, private[users] val users: MongoCollection[Document], val hasher: PasswordHash)(implicit val ioSched: Scheduler)
    extends LowPriorityMongoImplicits
    with StrictLogging {

  /** finds a user w/ the given username or email
    *
    * @param usernameOrEmail
    * @return
    */
  def findUser(usernameOrEmail: String): Future[Option[RegisteredUser]] = {
    val criteria = {
      Filters.or(
        Filters.equal(CreateUserRequest.userNameField, usernameOrEmail),
        Filters.equal(CreateUserRequest.emailField, usernameOrEmail)
      )
    }

    users
      .find(criteria)
      .limit(2) // in the odd case where we somehow get multiple users for the same username or email, we should know about it (and fail)
      .map { doc =>
        val result                              = BsonUtil.fromBson(doc).flatMap(_.as[CreateUserRequest].toTry)
        val CreateUserRequest(name, email, pwd) = result.get
        RegisteredUser(BsonUtil.idForDocument(doc), name, email, pwd)
      }
      .toFuture
      .map {
        case Seq(unique) => Option(unique)
        case Seq()       => None
        case _           => throw new IllegalStateException(s"Multiple users found w/ username or email '${usernameOrEmail}'")
      }
  }

  /**
    * Create a new user. If a user already exists w/ the same email then this should fail.
    *
    * Although we hash the password here when saving, the password  _SHOULD_ have already be hashed on the client side as well
    *
    * @param request
    * @return a future for the request which will simply succeed or fail
    */
  def createUser(request: CreateUserRequest): CancelableFuture[Either[GenericErrorResult, GenericMessageResult]] = {
    def fail(e: Throwable): CancelableFuture[Left[GenericErrorResult, Nothing]] = {
      e match {
        case mwe: MongoWriteException =>
          logger.error(s"Error creating user '${request.userName}', category:${mwe.getError.getCategory}, msg=${mwe.getError.getMessage}, code=${mwe.getError.getCode}")
          if (mwe.getError.getCategory == ErrorCategory.DUPLICATE_KEY) {
            def isEmail = mwe.getError.getMessage.contains("index: email")
            CancelableFuture.successful(Left(new GenericErrorResult("That user already exists")))
          } else {
            CancelableFuture.successful(Left(new GenericErrorResult(s"Error creating user '${request.userName}'")))
          }
        case _ =>
          logger.error(s"Error creating user '${request.userName}': ${e}")
          CancelableFuture.successful(Left(new GenericErrorResult(s"Computer says no when creating user '${request.userName}'")))
      }
      //email_1 dup key
    }
    request.validationErrors() match {

      case Seq() =>
        val reHashed    = hasher(request.hashedPassword)
        val safeRequest = request.copy(hashedPassword = reHashed)

        // we _should_ have indices which will catch duplicate usernames/emails on create
        try {
          val done: SingleObservable[Completed] = users.insertOne(safeRequest.asBsonDoc)

          done.monix.completedL.runToFuture.map(_ => Right(GenericMessageResult(s"Created user ${request.userName}"))).recoverWith {
            case e => fail(e)
          }
        } catch {
          case NonFatal(e) => fail(e)
        }
      case errors => fail(new GenericErrorResult(s"Error creating user '${request.userName}': ${errors.mkString(",")}", errors))
    }
  }
}

object UserRepoMongo extends LowPriorityMongoImplicits with StrictLogging {

  def apply(mongoDb: MongoDatabase, rootConfig: Config, usersCollectionName: String = "users")(implicit ioSched: Scheduler): CancelableFuture[UserRepoMongo] = {
    val coll = CollectionSettings(rootConfig, usersCollectionName)
    coll.ensureCreated(mongoDb).map { users =>
      apply(mongoDb, users, PasswordHash(rootConfig))
    }
  }

  def apply(mongoDb: MongoDatabase, users: MongoCollection[Document], hasher: PasswordHash)(implicit ioSched: Scheduler): UserRepoMongo = {
    new UserRepoMongo(mongoDb, users, hasher)
  }
}
