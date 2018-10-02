package com.github.mauricio.async.db.postgresql.pool

import com.github.mauricio.async.db.pool.{ConnectionPool, PoolConfiguration}
import com.github.mauricio.async.db.postgresql.DatabaseTestHelper
import org.specs2.mutable.Specification
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class ConcurrentConnectionPoolSpec extends Specification with DatabaseTestHelper {
  "pool" should {
    "execute simple queries in parallel" in {
      val pool = new ConnectionPool(new PostgreSQLConnectionFactory(defaultConfiguration), PoolConfiguration.Default)
      val queries = for (i <- 0 until 10) yield {
        Future {
          i -> pool.sendQuery(s"SELECT $i")
        }
      }

      await(Future.sequence(queries)).foreach {
        case (i, resultFuture) =>
          val rows = await(resultFuture).rows
          rows must beSome
          rows.get(0)(0) mustEqual i
      }
      success
    }
  }
}
