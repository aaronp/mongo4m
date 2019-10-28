package pipelines.mongo

import pipelines.audit.mongo.AuditServiceMongoSpec
import pipelines.users.mongo.{LoginHandlerMongoSpec, RefDataMongoSpec, UserRepoMongoSpec, UserRolesServiceSpec}

class PipelinesMongoSpec
    extends BasePipelinesMongoSpec
    with DbQueryTest
//    with ReactiveMongoSpec
//    with AuditServiceMongoSpec
//    with LoginHandlerMongoSpec
//    with MongoConnectSpec
//    with RefDataMongoSpec
//    with UserRepoMongoSpec
//    with UserRolesServiceSpec
//    with LowPriorityMongoImplicitsSpec
