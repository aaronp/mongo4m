package mongo4m

import monix.eval.Task
import monix.reactive.Observable
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.CreateCollectionOptions
import org.mongodb.scala.{Document, MongoCollection, MongoDatabase}
import org.slf4j.LoggerFactory

final class RichMongoDatabase(val mongoDb: MongoDatabase) extends AnyVal {
  import LowPriorityMongoImplicits._

  def collectionNames(): Observable[List[String]] = {
    mongoDb.listCollectionNames().monix.foldLeft(List.empty[String]) {
      case (list, next) => next :: list
    }
  }

  def createRequiredCollections(collectionNames: Set[String],
                                defaultOptions: CreateCollectionOptions =
                                  CreateCollectionOptions())
    : Observable[String] = {
    createRequiredCollections(collectionNames.map(_ -> defaultOptions).toMap)
  }

  /** creates the given map of collections. If the collection already exists, then happy days -- the 'create options' part is only used should
    * a collection not already exist.
    *
    * @param requiredCollectionOptionsByName the collections expected together with the 'create' options should they need to be created
    * @return the collections which were created by calling this function
    */
  def createRequiredCollections(
      requiredCollectionOptionsByName: Map[String, CreateCollectionOptions])
    : Observable[String] = {
    val requiredCollectionNames = requiredCollectionOptionsByName.keySet
    collectionNames().flatMap { existingCollections: List[String] =>
      val missing =
        requiredCollectionNames.filterNot(existingCollections.contains)
      val created: Observable[String] =
        Observable.fromIterable(missing).flatMap { missingCollection =>
          val options = requiredCollectionOptionsByName(missingCollection)
          mongoDb
            .createCollection(missingCollection, options)
            .monix
            .map(_ => missingCollection)
        }
      created
    }
  }

  /**
    * Ensure the collection configurable by the [[CollectionSettings]] is created
    *
    * @param config
    * @return a future of the collection
    */
  def ensureCreated(config: CollectionSettings) = {
    val logger = LoggerFactory.getLogger(getClass)

    // checks to see if the collection exists, and if not it is created
    mongoDb
      .createRequiredCollections(Set(config.collectionName), config.options)
      .toListL
      .flatMap { created: List[String] =>
        val collection: MongoCollection[Document] =
          mongoDb.getCollection(config.collectionName)
        if (created.nonEmpty) {
          import config.{indices, options}
          logger.info(
            s"Created ${created.mkString(",")} with $options, adding ${indices.size} indices: ${indices
              .mkString(",")}")

          val futures = indices.map { index =>
            val bson: BsonDocument = index.asBsonDoc
            collection
              .createIndex(bson, index.asOptions)
              .monix
              .map { name =>
                logger.info(s"Created index $name in $options")
                name
              }
              .completedL
          }

          Task.sequence(futures).map { _ =>
            collection
          }
        } else {
          logger.debug(s"Collection '${config.collectionName}' already exists")
          Task.now(collection)
        }
      }
  }
}
