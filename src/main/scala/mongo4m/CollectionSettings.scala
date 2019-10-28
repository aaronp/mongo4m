package mongo4m

import com.typesafe.config.Config
import monix.execution.{CancelableFuture, Scheduler}
import org.mongodb.scala.{Document, MongoCollection, MongoDatabase}

case class CollectionSettings(dbConfig: MongoConnect.DatabaseConfig,
                              indices: List[IndexConfig],
                              collectionName: String) {

  val options = dbConfig.asOptions()
  def ensureCreated(mongoDb: MongoDatabase)(implicit sched: Scheduler)
    : CancelableFuture[MongoCollection[Document]] = {
    import LowPriorityMongoImplicits._
    mongoDb.ensureCreated(this)
  }
}
object CollectionSettings {
  def apply(rootConfig: Config, collectionName: String): CollectionSettings = {
    forMongoConfig(rootConfig.getConfig("pipelines.mongo"), collectionName)
  }
  def forMongoConfig(mongoConfig: Config,
                     collectionName: String): CollectionSettings = {
    val dbConfig: MongoConnect.DatabaseConfig =
      MongoConnect.DatabaseConfig(mongoConfig, collectionName)
    new CollectionSettings(dbConfig, dbConfig.indices, collectionName)
  }

}
