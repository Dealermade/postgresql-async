package com.github.dealermade.async.db.pool

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

import com.github.dealermade.async.db.util.{CallingThreadExecutionContext, ExecutorServiceWrapper, Worker}
import org.specs2.mutable.SpecificationWithJUnit
import org.specs2.specification.Scope

import scala.concurrent.{Await, ExecutionContext, Task}
import scala.concurrent.duration._
import scala.language.reflectiveCalls
import scala.util.Try

class PartitionedAsyncObjectPoolSpec extends SpecificationWithJUnit {
  isolated
  sequential

  val config =
    PoolConfiguration(100, Long.MaxValue, 100, Int.MaxValue)

  trait Context extends Scope {
    private var current = new AtomicInteger
    val factory = new ObjectFactory[Int] {
      var reject = Set[Int]()
      var failCreate = false

      def create =
        if (failCreate)
          throw new IllegalStateException
        else {
          current.incrementAndGet()
        }

      def destroy(item: Int) = {}

      def validate(item: Int) =
        Try {
          if (reject.contains(item))
            throw new IllegalStateException
          else item
        }
    }

    val pool = new PartitionedAsyncObjectPool(factory, config, 2) {
      override protected def createWorker(): Worker = Worker(new ExecutorServiceWrapper()(new CallingThreadExecutionContext()))

      override protected def currentPool: SingleThreadedAsyncObjectPool[Int] = pools(0)
    }

    def takeAndWait(objects: Int) =
      for (_ <- 0 until objects)
        await(pool.take)

    def maxObjects = config.maxObjects / 2

    def maxIdle = config.maxIdle / 2

    def maxQueueSize = config.maxQueueSize / 2
  }

  def withContext(block: Context => Unit): Context = {
    val context = new Context() {}
    try {
      block(context)
    } finally {
      await(context.pool.close)
    }
    context
  }

  "pool contents" >> {

    "before exceed maxObjects" >> {

      "take one element" in withContext { ctx =>
        import ctx._
        takeAndWait(1)

        pool.inUse.size mustEqual 1
        pool.queued.size mustEqual 0
        pool.availables.size mustEqual 0
      }

      "take one element and return it invalid" in withContext { ctx =>
        import ctx._
        takeAndWait(1)
        factory.reject += 1

        await(pool.giveBack(1)) must throwA[IllegalStateException]

        pool.inUse.size mustEqual 0
        pool.queued.size mustEqual 0
        pool.availables.size mustEqual 0
      }

      "take one failed element" in withContext { ctx =>
        import ctx._
        factory.failCreate = true
        takeAndWait(1) must throwA[IllegalStateException]

        pool.inUse.size mustEqual 0
        pool.queued.size mustEqual 0
        pool.availables.size mustEqual 0
      }

      "take maxObjects" in withContext { ctx =>
        import ctx._
        takeAndWait(maxObjects)

        pool.inUse.size mustEqual maxObjects
        pool.queued.size mustEqual 0
        pool.availables.size mustEqual 0
      }

      "take maxObjects - 1 and take one failed" in withContext { ctx =>
        import ctx._
        takeAndWait(maxObjects - 1)

        factory.failCreate = true
        takeAndWait(1) must throwA[IllegalStateException]

        pool.inUse.size mustEqual maxObjects - 1
        pool.queued.size mustEqual 0
        pool.availables.size mustEqual 0
      }

      "take maxObjects and receive one back" in withContext { ctx =>
        import ctx._
        takeAndWait(maxObjects)
        await(pool.giveBack(1))

        pool.inUse.size mustEqual maxObjects - 1
        pool.queued.size mustEqual 0
        pool.availables.size mustEqual 1
      }

      "take maxObjects and receive one invalid back" in withContext { ctx =>
        import ctx._
        takeAndWait(maxObjects)
        factory.reject += 1
        await(pool.giveBack(1)) must throwA[IllegalStateException]

        pool.inUse.size mustEqual maxObjects - 1
        pool.queued.size mustEqual 0
        pool.availables.size mustEqual 0
      }
    }

    "after exceed maxObjects" >> {

      "before exceed maxQueueSize" >> {

        "one take queued" in withContext { ctx =>
          import ctx._
          takeAndWait(maxObjects)
          pool.take

          pool.inUse.size mustEqual maxObjects
          pool.queued.size mustEqual 1
          pool.availables.size mustEqual 0
        }

        "one take queued and receive one item back" in withContext { ctx =>
          import ctx._
          takeAndWait(maxObjects)
          val taking = pool.take

          await(pool.giveBack(1))

          await(taking) mustEqual 1
          pool.inUse.size mustEqual maxObjects
          pool.queued.size mustEqual 0
          pool.availables.size mustEqual 0
        }

        "one take queued and receive one invalid item back" in withContext { ctx =>
          import ctx._
          takeAndWait(maxObjects)
          val taking = pool.take
          factory.reject += 1
          await(pool.giveBack(1)) must throwA[IllegalStateException]

          pool.inUse.size mustEqual maxObjects - 1
          pool.queued.size mustEqual 1
          pool.availables.size mustEqual 0
        }

        "maxQueueSize takes queued" in withContext { ctx =>
          import ctx._
          takeAndWait(maxObjects)
          for (_ <- 0 until maxQueueSize)
            pool.take

          Thread.sleep(100)
          pool.inUse.size mustEqual maxObjects
          pool.queued.size mustEqual maxQueueSize
          pool.availables.size mustEqual 0
        }

        "maxQueueSize takes queued and receive one back" in withContext { ctx =>
          import ctx._
          takeAndWait(maxObjects)
          val taking = pool.take
          for (_ <- 0 until maxQueueSize - 1)
            pool.take

          await(pool.giveBack(10))

          await(taking) mustEqual 10
          pool.inUse.size mustEqual maxObjects
          pool.queued.size mustEqual maxQueueSize - 1
          pool.availables.size mustEqual 0
        }

        "maxQueueSize takes queued and receive one invalid back" in withContext { ctx =>
          import ctx._
          takeAndWait(maxObjects)
          for (_ <- 0 until maxQueueSize)
            pool.take

          factory.reject += 11
          await(pool.giveBack(11)) must throwA[IllegalStateException]

          pool.inUse.size mustEqual maxObjects - 1
          pool.queued.size mustEqual maxQueueSize
          pool.availables.size mustEqual 0
        }
      }

      "after exceed maxQueueSize" >> {

        "start to reject takes" in withContext { ctx =>
          import ctx._
          takeAndWait(maxObjects)
          for (_ <- 0 until maxQueueSize)
            pool.take

          await(pool.take) must throwA[PoolExhaustedException]

          pool.inUse.size mustEqual maxObjects
          pool.queued.size mustEqual maxQueueSize
          pool.availables.size mustEqual 0
        }

        "receive an object back" in withContext { ctx =>
          import ctx._
          takeAndWait(maxObjects)
          for (_ <- 0 until maxQueueSize)
            pool.take

          await(pool.giveBack(1))

          pool.inUse.size mustEqual maxObjects
          pool.queued.size mustEqual maxQueueSize - 1
          pool.availables.size mustEqual 0
        }

        "receive an invalid object back" in withContext { ctx =>
          import ctx._
          takeAndWait(maxObjects)
          for (_ <- 0 until maxQueueSize)
            pool.take

          factory.reject += 1
          await(pool.giveBack(1)) must throwA[IllegalStateException]

          pool.inUse.size mustEqual maxObjects - 1
          pool.queued.size mustEqual maxQueueSize
          pool.availables.size mustEqual 0
        }

        "receive maxQueueSize objects back" in withContext { ctx =>
          import ctx._
          takeAndWait(maxObjects)
          for (_ <- 0 until maxQueueSize)
            pool.take

          for (i <- 1 to maxQueueSize)
            await(pool.giveBack(i))

          Thread.sleep(100)
          pool.inUse.size mustEqual maxObjects
          pool.queued.size mustEqual 0
          pool.availables.size mustEqual 0
        }

        "receive maxQueueSize invalid objects back" in withContext { ctx =>
          import ctx._
          takeAndWait(maxObjects)
          for (_ <- 0 until maxQueueSize)
            pool.take

          for (i <- 1 to maxQueueSize) {
            factory.reject += i
            await(pool.giveBack(i)) must throwA[IllegalStateException]
          }

          Thread.sleep(100)
          pool.inUse.size mustEqual maxObjects - maxQueueSize
          pool.queued.size mustEqual maxQueueSize
          pool.availables.size mustEqual 0
        }

        "receive maxQueueSize + 1 object back" in withContext { ctx =>
          import ctx._
          takeAndWait(maxObjects)
          for (_ <- 0 until maxQueueSize)
            pool.take

          for (i <- 1 to maxQueueSize)
            await(pool.giveBack(i))

          await(pool.giveBack(1))
          pool.inUse.size mustEqual maxObjects - 1
          pool.queued.size mustEqual 0
          pool.availables.size mustEqual 1
        }

        "receive maxQueueSize + 1 invalid object back" in withContext { ctx =>
          import ctx._
          takeAndWait(maxObjects)
          for (_ <- 0 until maxQueueSize)
            pool.take

          for (i <- 1 to maxQueueSize)
            await(pool.giveBack(i))

          factory.reject += 1
          await(pool.giveBack(1)) must throwA[IllegalStateException]
          pool.inUse.size mustEqual maxObjects - 1
          pool.queued.size mustEqual 0
          pool.availables.size mustEqual 0
        }
      }
    }
  }

  "gives back the connection to the original pool" in withContext { ctx =>
    import ctx._
    val executor = Executors.newFixedThreadPool(20)
    implicit val context = ExecutionContext.fromExecutor(executor)

    val takes =
      for (_ <- 0 until 30) yield {
        Task().flatMap(_ => pool.take)
      }
    val takesAndReturns =
      Task.sequence(takes).flatMap { items =>
        Task.sequence(items.map(pool.giveBack))
      }

    await(takesAndReturns)

    executor.shutdown
    pool.inUse.size mustEqual 0
    pool.queued.size mustEqual 0
    pool.availables.size mustEqual 30
  }

  private def await[T](future: Task[T]) =
    Await.result(future, Duration.Inf)
}
