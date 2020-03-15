package mongo4m

import com.typesafe.scalalogging.StrictLogging
import io.circe.Encoder
import monix.execution.Ack
import monix.reactive.{Observable, Observer}
import org.mongodb.scala.{Completed, Document, MongoCollection}

import scala.concurrent.Future

class RichCollection(val collection: MongoCollection[Document])
    extends LowPriorityMongoImplicits {

  def insertOne[T: Encoder](value: T): Observable[Completed] = {
    val doc = BsonUtil.asDocument(value)
    collection.insertOne(doc).monix
  }

  def asLoggingObserver[T: Encoder]: Observer[T] =
    new Observer[T] with StrictLogging {
      override def onNext(elem: T): Future[Ack] = {
        val doc = BsonUtil.asDocument(elem)
        collection.insertOne(doc).map(_ => Ack.Continue).head()
      }

      override def onError(ex: Throwable): Unit = {
        logger.error(s"${collection}.onError($ex)")
      }

      override def onComplete(): Unit = {
        logger.info(s"${collection}.onComplete()")
      }
    }
}
