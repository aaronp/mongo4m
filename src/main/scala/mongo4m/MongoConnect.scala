package mongo4m

import cats.effect.{IO, Resource}
import com.mongodb.ConnectionString
import com.typesafe.config.{Config, ConfigException, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import monix.reactive.Observable
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.model.{CreateCollectionOptions, IndexOptions}
import org.mongodb.scala.{
  Document,
  MongoClient,
  MongoClientSettings,
  MongoCollection,
  MongoCredential,
  MongoDatabase
}

import scala.util.Try

final class MongoConnect(mongoConfig: Config) extends StrictLogging {

  def user = mongoConfig.getString("user")

  def database: String =
    mongoConfig.getString("database").ensuring(_.nonEmpty, "'database' not set")

  def settingsForCollection(collectionName: String,
                            basedOn: String = "common"): CollectionSettings = {
    val conf = configForCollection(collectionName, basedOn = basedOn)
    CollectionSettings.forMongoConfig(conf, collectionName)
  }

  def collectionObservable(
      collectionName: String,
      basedOn: String = "common"): Observable[MongoCollection[Document]] = {
    val cs = settingsForCollection(collectionName, basedOn = basedOn)
    collectionObservable(cs)
  }

  def collectionObservable(
      cs: CollectionSettings): Observable[MongoCollection[Document]] = {
    mongoDbResourceObservable.flatMap { db =>
      Observable.fromTask(cs.ensureCreated(db))
    }
  }

  def configForCollection(collectionName: String,
                          basedOn: String = "common",
                          maxDocuments: Int = 0,
                          capped: Boolean = true,
                          maxSize: String = "500M",
                          pollFreq: String = "100ms"): Config = {
    val c = ConfigFactory.parseString(s"""databases {
         |    ${collectionName} = $${databases.${basedOn}}
         |    ${collectionName}.maxDocuments: $maxDocuments
         |    ${collectionName}.capped: $capped
         |    ${collectionName}.maxSizeInBytes: $maxSize
         |    ${collectionName}.pollFrequency: $pollFreq
         |}""".stripMargin)
    c.withFallback(mongoConfig).resolve()
  }

  def uri = mongoConfig.getString("uri")

  private def mongoDb(): MongoDatabase = {
    client.getDatabase(database)
  }

  lazy val mongoResource: Resource[IO, (MongoClient, MongoDatabase)] = {
    def connect(): (MongoClient, MongoDatabase) = {
      val c = client
      c -> c.getDatabase(database)
    }

    Resource.make(IO(connect())) {
      case (client, _) =>
        IO(client.close())
    }
  }

  def mongoDbResourceObservable: Observable[MongoDatabase] =
    Observable.fromResource(mongoDbResource)

  lazy val mongoDbResource: Resource[IO, MongoDatabase] = {
    mongoResource.flatMap {
      case (_, b) => Resource.pure[IO, MongoDatabase](b)
    }
  }

  def useClient[A](thunk: (MongoClient, MongoDatabase) => A): A = {
    val io = mongoResource.use { pear =>
      IO(thunk(pear._1, pear._2))
    }
    io.unsafeRunSync()
  }

  def use[A](thunk: MongoDatabase => A): A = {
    mongoDbResource
      .use { db =>
        IO(thunk(db))
      }
      .unsafeRunSync()
  }

  def client: MongoClient = {
    val pwd = mongoConfig.getString("password").toCharArray
    val creds = MongoCredential.createCredential(user, database, pwd)
    logger.info(s"$user connecting to db $database at '$uri'")
    MongoClientSettings
      .builder()
      .applyConnectionString(new ConnectionString(uri))
      .codecRegistry(DEFAULT_CODEC_REGISTRY)
      .credential(creds)
      .build()
    MongoClient(uri)
  }
}

object MongoConnect extends LowPriorityMongoImplicits {

  case class IndexConfig(config: Config) {

    private def unique = config.getBoolean("unique")

    private def background = config.getBoolean("background")

    private def field = config.getString("field")

    private def fields: Seq[String] = {
      if (config.hasPath("fields")) {
        try {
          import scala.collection.JavaConverters._
          config.getStringList("fields").asScala.toSeq
        } catch {
          case _: ConfigException =>
            config
              .getString("fields")
              .split(",", -1)
              .map(_.trim)
              .filterNot(_.isEmpty)
              .toSeq
        }
      } else {
        Nil
      }

    }

    private def ascending = Try(config.getBoolean("ascending")).getOrElse(true)

    private def bson: Json = {
      fields match {
        case Seq() => Json.obj(field -> Json.fromInt(if (ascending) 1 else -1))
        case many =>
          val map: Seq[(String, Json)] = many.map { name =>
            name -> Json.fromInt(if (ascending) 1 else -1)
          }
          Json.obj(map: _*)
      }
    }

    def asBsonDoc = {
      BsonUtil.asDocument(bson)
    }

    def asOptions: IndexOptions = {
      IndexOptions().unique(unique).background(background)
    }

    def name: String = {
      if (config.hasPath("name")) {
        config.getString("name")
      } else {
        field + "Index"
      }
    }

  }

  case class DatabaseConfig(val config: Config) {
    def capped = config.getBoolean("capped")

    def maxSizeInBytes: Long = config.getMemorySize("maxSizeInBytes").toBytes

    def maxDocuments = config.getLong("maxDocuments")

    def indices: List[IndexConfig] = {
      import scala.collection.JavaConverters._
      config.getConfigList("indices").asScala.map(IndexConfig.apply).toList
    }

    def asOptions(): CreateCollectionOptions = {
      val opts =
        CreateCollectionOptions().capped(capped).maxDocuments(maxDocuments)
      maxSizeInBytes match {
        case 0L => opts
        case n  => opts.sizeInBytes(n)
      }
    }
  }

  object DatabaseConfig {
    def apply(mongoConfig: Config, name: String): DatabaseConfig =
      DatabaseConfig(mongoConfig.getConfig(s"databases.$name"))
  }

  def use[A](rootConfig: Config)(
      thunk: (MongoClient, MongoDatabase) => A): A = {
    val io = resource(rootConfig).use { pear =>
      IO(thunk(pear._1, pear._2))
    }
    io.unsafeRunSync()
  }

  def resource(
      rootConfig: Config): Resource[IO, (MongoClient, MongoDatabase)] = {
    def connect(): (MongoClient, MongoDatabase) = {
      val c = MongoConnect(rootConfig)
      c.client -> c.mongoDb
    }

    Resource.make(IO(connect())) {
      case (client, _) =>
        IO(client.close())
    }
  }

  def apply(rootConfig: Config): MongoConnect =
    forMongoConfig(rootConfig.getConfig("mongo4m"))

  def forMongoConfig(config: Config): MongoConnect = new MongoConnect(config)
}
