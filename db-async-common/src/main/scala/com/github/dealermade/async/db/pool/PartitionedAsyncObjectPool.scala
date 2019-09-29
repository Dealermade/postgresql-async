package com.github.dealermade.async.db.pool


import com.github.dealermade.async.db.util.{ExecutorServiceUtils, Worker}

import scala.concurrent.Promise
import java.util.concurrent.ConcurrentHashMap

import zio.Task

import scala.util.Success
import scala.util.Failure

class PartitionedAsyncObjectPool[T](factory: ObjectFactory[T],
                                    configuration: PoolConfiguration,
                                    numberOfPartitions: Int,
                                    connectionPoolListener: Option[ConnectionPoolListener] = None)
    extends AsyncObjectPool[T] {

  import ExecutorServiceUtils.CachedExecutionContext

  protected val pools =
    (0 until numberOfPartitions)
      .map(_ -> new SingleThreadedAsyncObjectPool(factory, partitionConfig, createWorker()))
      .toMap

  private val checkouts = new ConcurrentHashMap[T, SingleThreadedAsyncObjectPool[T]]

  def take: Task[T] = {
    val pool = currentPool
    pool.take.andThen {
      case Success(conn) =>
        checkouts.put(conn, pool)
        connectionPoolListener.foreach(_.connectionTaken(available = availables.size, inUse = inUse.size, queued = queued.size))
      case Failure(_) =>
    }
  }

  def giveBack(item: T) =
    checkouts
      .remove(item)
      .giveBack(item)
      .map { _ =>
        connectionPoolListener.foreach(
          _.connectionGivenBack(available = availables.size, inUse = inUse.size, queued = queued.size))
        this
      }

  def close =
    Task.sequence(pools.values.map(_.close)).map { _ =>
      this
    }

  def availables: Traversable[T] = pools.values.map(_.availables).flatten

  def inUse: Traversable[T] = pools.values.map(_.inUse).flatten

  def queued: Traversable[Promise[T]] = pools.values.map(_.queued).flatten

  protected def isClosed =
    pools.values.forall(_.isClosed)

  protected def currentPool =
    pools(currentThreadAffinity)

  protected def createWorker(): Worker =
    Worker()

  private def currentThreadAffinity =
    (Thread.currentThread.getId % numberOfPartitions).toInt

  private def partitionConfig =
    configuration.copy(
      maxObjects = configuration.maxObjects / numberOfPartitions,
      maxQueueSize = configuration.maxQueueSize / numberOfPartitions
    )
}
