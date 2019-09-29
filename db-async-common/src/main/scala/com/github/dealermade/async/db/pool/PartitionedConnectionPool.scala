package com.github.dealermade.async.db.pool;

import com.github.dealermade.async.db.util.ExecutorServiceUtils
import com.github.dealermade.async.db.{Connection, QueryResult}
import zio.Task

import scala.concurrent.{ExecutionContext}

class PartitionedConnectionPool[T <: Connection](factory: ObjectFactory[T],
                                                 configuration: PoolConfiguration,
                                                 numberOfPartitions: Int,
                                                 executionContext: ExecutionContext = ExecutorServiceUtils.CachedExecutionContext,
                                                 connectionPoolListener: Option[ConnectionPoolListener] = None)
    extends PartitionedAsyncObjectPool[T](factory, configuration, numberOfPartitions, connectionPoolListener)
    with Connection {

  def disconnect: Task[Connection] =
    if (this.isConnected) {
      this.close.map(item => this)(executionContext)
    } else {
      Task.succeed(this)
    }

  def connect: Task[Connection] = Task.succeed(this)

  def isConnected: Boolean = !this.isClosed

  def sendQuery(query: String): Task[QueryResult] =
    this.use(_.sendQuery(query))(executionContext)

  def sendPreparedStatement(query: String, values: Seq[Any] = List()): Task[QueryResult] =
    this.use(_.sendPreparedStatement(query, values))(executionContext)

  override def inTransaction[A](f: Connection => Task[A])(implicit context: ExecutionContext = executionContext): Task[A] =
    this.use(_.inTransaction[A](f)(context))(executionContext)
}
