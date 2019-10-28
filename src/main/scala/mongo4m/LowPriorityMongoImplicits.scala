package mongo4m

import io.circe.Encoder
import mongo4m.MongoReactive.MongoObservable
import org.mongodb.scala.{Document, MongoCollection, MongoDatabase}

trait LowPriorityMongoImplicits {
  implicit def mongoObsAsReactiveSubscriber[A](obs: MongoObservable[A]) =
    new RichMongoObservable[A](obs)
  implicit def asRichCollection(collection: MongoCollection[Document]) =
    new RichCollection(collection)
  implicit def asRichDocument(document: Document) = new RichDocument(document)
  implicit def asRichDb(mongoDatabase: MongoDatabase) =
    new RichMongoDatabase(mongoDatabase)
  implicit def asRichEncodable[A: Encoder](value: A) = new RichEncodable(value)
}

object LowPriorityMongoImplicits extends LowPriorityMongoImplicits
