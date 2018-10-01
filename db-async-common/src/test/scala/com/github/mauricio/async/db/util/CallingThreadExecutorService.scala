package com.github.mauricio.async.db.util

import scala.concurrent.ExecutionContext

class CallingThreadExecutorService extends ExecutionContext {
  override def execute(runnable: Runnable): Unit = runnable.run()

  override def reportFailure(cause: Throwable): Unit = throw cause
}
