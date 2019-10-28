package pipelines.mongo

import io.circe.Encoder
import org.mongodb.scala.{Document, MongoCollection, MongoDatabase}
import pipelines.mongo.MongoReactive.MongoObservable

trait LowPriorityMongoImplicits {
  implicit def mongoObsAsReactiveSubscriber[A](obs: MongoObservable[A]) = new RichMongoObservable[A](obs)
  implicit def asRichCollection(collection: MongoCollection[Document])  = new RichCollection(collection)
  implicit def asRichDocument(document: Document)                       = new RichDocument(document)
  implicit def asRichDb(mongoDatabase: MongoDatabase)                   = new RichMongoDatabase(mongoDatabase)
  implicit def asRichEncodable[A: Encoder](value: A)                    = new RichEncodable(value)
}

object LowPriorityMongoImplicits extends LowPriorityMongoImplicits
