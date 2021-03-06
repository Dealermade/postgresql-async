package com.github.dealermade.async.db.pool;

import com.github.dealermade.async.db.util.ExecutorServiceUtils
import com.github.dealermade.async.db.{QueryResult, Connection}
import scala.concurrent.{ExecutionContext, Future}

class PartitionedConnectionPool[T <: Connection](factory: ObjectFactory[T],
                                                 configuration: PoolConfiguration,
                                                 numberOfPartitions: Int,
                                                 executionContext: ExecutionContext = ExecutorServiceUtils.CachedExecutionContext,
                                                 connectionPoolListener: Option[ConnectionPoolListener] = None)
    extends PartitionedAsyncObjectPool[T](factory, configuration, numberOfPartitions, connectionPoolListener)
    with Connection {

  def disconnect: Future[Connection] =
    if (this.isConnected) {
      this.close.map(item => this)(executionContext)
    } else {
      Future.successful(this)
    }

  def connect: Future[Connection] = Future.successful(this)

  def isConnected: Boolean = !this.isClosed

  def sendQuery(query: String): Future[QueryResult] =
    this.use(_.sendQuery(query))(executionContext)

  def sendPreparedStatement(query: String, values: Seq[Any] = List()): Future[QueryResult] =
    this.use(_.sendPreparedStatement(query, values))(executionContext)

  override def inTransaction[A](f: Connection => Future[A])(implicit context: ExecutionContext = executionContext): Future[A] =
    this.use(_.inTransaction[A](f)(context))(executionContext)
}
