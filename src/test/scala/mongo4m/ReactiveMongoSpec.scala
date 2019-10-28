package mongo4m

trait ReactiveMongoSpec extends BasePipelinesMongoSpec {

  "ReactiveMongo" should {
    "be able to return a stream of query results based on a stream of mongo/query tuples" in {
      Schedulers.using { implicit sched =>
        ???
      }
    }
  }
}
