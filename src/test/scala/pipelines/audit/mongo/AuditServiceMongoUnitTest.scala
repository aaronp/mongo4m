package pipelines.audit.mongo

import java.time.ZonedDateTime

import org.scalatest.{Matchers, WordSpec}

class AuditServiceMongoUnitTest extends WordSpec with Matchers {
  "AuditServiceMongo.asFindCriteria" should {
    "produce criteria" in {
      val Some(criteria) = AuditServiceMongo.asFindCriteria(before = Option(ZonedDateTime.now), after = Option(ZonedDateTime.now))
      criteria should not be (null)
    }
  }
}
