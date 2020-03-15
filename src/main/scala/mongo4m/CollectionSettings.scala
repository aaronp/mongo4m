package mongo4m

import com.typesafe.config.Config
import mongo4m.MongoConnect.IndexConfig
import monix.eval.Task
import org.mongodb.scala.{Document, MongoCollection, MongoDatabase}

/**
  * Represents a means to create a new collection with indices and settings
  * @param dbConfig
  * @param indices
  * @param collectionName
  */
case class CollectionSettings(dbConfig: MongoConnect.DatabaseConfig,
                              indices: List[IndexConfig],
                              collectionName: String) {

  val options = dbConfig.asOptions()
  def ensureCreated(mongoDb: MongoDatabase): Task[MongoCollection[Document]] = {
    import LowPriorityMongoImplicits._
    mongoDb.ensureCreated(this)
  }
  def withName(newCollectionName: String): CollectionSettings =
    copy(collectionName = newCollectionName)
}
object CollectionSettings {
  def apply(rootConfig: Config, collectionName: String): CollectionSettings = {
    forMongoConfig(rootConfig.getConfig("mongo4m"), collectionName)
  }
  def forMongoConfig(mongoConfig: Config,
                     collectionName: String): CollectionSettings = {
    val dbConfig: MongoConnect.DatabaseConfig =
      MongoConnect.DatabaseConfig(mongoConfig, collectionName)
    new CollectionSettings(dbConfig, dbConfig.indices, collectionName)
  }

}
