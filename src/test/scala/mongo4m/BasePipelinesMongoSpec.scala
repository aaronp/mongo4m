package mongo4m

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import dockerenv.BaseMongoSpec
import org.mongodb.scala.{MongoClient, MongoDatabase}

import scala.concurrent.duration._
import scala.util.Success

abstract class BasePipelinesMongoSpec extends BaseMongoSpec with LowPriorityMongoImplicits with StrictLogging {

  override def testTimeout = 15.seconds

  lazy val rootConfig = ConfigFactory.load()

  lazy val connect = MongoConnect(rootConfig)

  def newClient(): MongoClient = MongoConnect(rootConfig).client

  var mongoClient: MongoClient = null
  def mongoDb: MongoDatabase   = mongoClient.getDatabase("test-db")


  override def beforeAll(): Unit = {
    super.beforeAll()

    val listOutput = eventually {
      val Success((0, output)) = dockerEnv.runInScriptDir("mongo.sh", "listUsers.js")
      output
    }

    if (!listOutput.contains("serviceUser")) {
      val createOutput = eventually {
        val Success((0, output)) = dockerEnv.runInScriptDir("mongo.sh", "createUser.js")
        output
      }
      createOutput should include("serviceUser")
    }

    mongoClient = newClient()
  }

  override def afterAll(): Unit = {
    import scala.collection.JavaConverters._

    if (mongoClient != null) {
      mongoClient.close()
      mongoClient = null
    }

    val threads = Thread.getAllStackTraces.asScala.collect {
      case (thread, stack) if stack.exists(_.getClassName.contains("mongodb")) => thread
    }
    logger.error(s"""MongoClient.close() ... doesn't. Interrupting ${threads.size} threads""".stripMargin)
    threads.foreach(_.stop())

    super.afterAll()
  }
}
