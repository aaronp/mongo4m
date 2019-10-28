package pipelines.mongo

import org.mongodb.scala.model.CreateCollectionOptions
import pipelines.Schedulers

trait LowPriorityMongoImplicitsSpec extends BasePipelinesMongoSpec {

  "RichMongoDatabase.createRequiredCollections" should {
    "createRequiredCollections" in {
      Schedulers.using { implicit scheduler =>
        val capped  = s"capped${System.currentTimeMillis}"
        val limited = s"limit${System.currentTimeMillis}"

        val obs = mongoDb.createRequiredCollections(
          Map(
            capped  -> CreateCollectionOptions().capped(true).sizeInBytes(12345).usePowerOf2Sizes(true),
            limited -> CreateCollectionOptions().maxDocuments(2)
          ))

        val created: List[String] = obs.toListL.runSyncUnsafe(testTimeout)
        created should contain only (capped, limited)

        withClue("Our collections should now exist, so running our cold-observable a second time should have no effect") {
          val shouldBeEmpty: List[String] = obs.toListL.runSyncUnsafe(testTimeout)
          shouldBeEmpty should be(empty)
        }
      }
    }
  }
}
