package pipelines.mongo

// helper class to ensure mongo is running, should we want to spin it up and test w/ some main methods
object StartMongo extends App {
  dockerenv.mongo().start()
}
