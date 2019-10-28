package pipelines.mongo
import monix.reactive.Observable
import pipelines.mongo.MongoReactive.{MongoObservable, RPublisher}

class RichMongoObservable[A](obs: MongoObservable[A]) {

  def asPublisher: RPublisher[A] = {
    new MongoReactive.ReactivePublisherForObservable(obs)
  }

  def monix: Observable[A] = Observable.fromReactivePublisher(asPublisher)
}
