package mongo4m

import com.typesafe.scalalogging.StrictLogging
import monix.execution.schedulers.SchedulerService
import monix.execution.{ExecutionModel, Scheduler, UncaughtExceptionReporter}


object Schedulers {

  def use[A](sched: SchedulerService)(f: Scheduler => A): A = {
    try {
      f(sched)
    } finally {
      sched.shutdown()
    }
  }

  def using[A](f: Scheduler => A): A = use(compute())(f)

  object LoggingReporter extends UncaughtExceptionReporter with StrictLogging {
    override def reportFailure(ex: Throwable): Unit = {
      logger.error(s"Failure: $ex", ex)
    }
  }

  def io(name: String = "mongo4m-io", daemonic: Boolean = true): SchedulerService = {
    Scheduler.io(name, daemonic = daemonic, reporter = LoggingReporter, executionModel = ExecutionModel.Default)
  }

  def computeAsync(name: String = "mongo4m-compute"): SchedulerService = {
    compute(executionModel = ExecutionModel.AlwaysAsyncExecution)
  }

  def compute(name: String = "mongo4m-compute", daemonic: Boolean = true, executionModel: ExecutionModel = ExecutionModel.Default): SchedulerService = {
    Scheduler.computation(name = name, daemonic = daemonic, reporter = LoggingReporter, executionModel = executionModel)
  }

}
