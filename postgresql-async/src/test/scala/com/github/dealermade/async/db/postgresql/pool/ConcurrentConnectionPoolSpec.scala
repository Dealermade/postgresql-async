package com.github.dealermade.async.db.postgresql.pool

import com.github.dealermade.async.db.QueryResult
import com.github.dealermade.async.db.pool.{ConnectionPool, PoolConfiguration}
import com.github.dealermade.async.db.postgresql.DatabaseTestHelper
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Task

class ConcurrentConnectionPoolSpec extends Specification with DatabaseTestHelper {

  trait Context extends Scope {
    val poolConfig: PoolConfiguration = PoolConfiguration.Default
    val pool = new ConnectionPool(new PostgreSQLConnectionFactory(defaultConfiguration), poolConfig)
  }

  "connection pool" should {
    "execute simple queries in parallel" in new Context {
      val queries = for (i <- 0 until poolConfig.maxObjects) yield {
        Task(i -> pool.sendQuery(s"SELECT $i"))
      }

      assertResultsMatchIndexes(queries)
    }
    "execute queued queries in parallel" in new Context {
      val queries = for (i <- 0 until poolConfig.maxObjects + poolConfig.maxQueueSize) yield {
        Task(i -> pool.sendQuery(s"SELECT $i"))
      }

      assertResultsMatchIndexes(queries)
    }
  }

  private def assertResultsMatchIndexes(queries: Seq[Task[(Int, Task[QueryResult])]]): Unit = {
    await(Task.sequence(queries)).foreach {
      case (i, resultTask) =>
        val rows = await(resultTask).rows
        rows.map(x => x.apply(0)(0)) === Some(i)
    }
  }
}
