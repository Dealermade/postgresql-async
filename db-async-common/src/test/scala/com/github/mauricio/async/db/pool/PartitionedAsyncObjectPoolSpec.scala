package com.github.mauricio.async.db.pool

import java.util.concurrent.atomic.AtomicInteger

import org.specs2.mutable.Specification

import scala.util.Try
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.Future
import org.specs2.mutable.SpecificationWithJUnit

import language.reflectiveCalls
import com.github.mauricio.async.db.util.{CallingThreadExecutorService, ExecutorServiceUtils, ExecutorServiceWrapper, Worker}

import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors

import org.specs2.specification.Scope

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

        private val workerFactory = () => Worker(new ExecutorServiceWrapper()(new CallingThreadExecutorService()))
        val pool = new PartitionedAsyncObjectPool(factory, config, 2, workerFactory = workerFactory) {
            override protected def currentPool: SingleThreadedAsyncObjectPool[Int] = pools(0)
        }

        def takeAndWait(objects: Int) =
            for (_ <- 0 until objects)
                await(pool.take)

        def maxObjects = config.maxObjects / 2
        def maxIdle = config.maxIdle / 2
        def maxQueueSize = config.maxQueueSize / 2
    }

    "pool contents" >> {

        "before exceed maxObjects" >> {

            "take one element" in new Context {
                takeAndWait(1)

                pool.inUse.size mustEqual 1
                pool.queued.size mustEqual 0
                pool.availables.size mustEqual 0
            }

            "take one element and return it invalid" in new Context {
                takeAndWait(1)
                factory.reject += 1

                await(pool.giveBack(1)) must throwA[IllegalStateException]

                pool.inUse.size mustEqual 0
                pool.queued.size mustEqual 0
                pool.availables.size mustEqual 0
            }

            "take one failed element" in new Context {
                factory.failCreate = true
                takeAndWait(1) must throwA[IllegalStateException]

                pool.inUse.size mustEqual 0
                pool.queued.size mustEqual 0
                pool.availables.size mustEqual 0
            }

            "take maxObjects" in new Context {
                takeAndWait(maxObjects)

                pool.inUse.size mustEqual maxObjects
                pool.queued.size mustEqual 0
                pool.availables.size mustEqual 0
            }

            "take maxObjects - 1 and take one failed" in new Context {
                takeAndWait(maxObjects - 1)

                factory.failCreate = true
                takeAndWait(1) must throwA[IllegalStateException]

                pool.inUse.size mustEqual maxObjects - 1
                pool.queued.size mustEqual 0
                pool.availables.size mustEqual 0
            }

            "take maxObjects and receive one back" in new Context {
                takeAndWait(maxObjects)
                await(pool.giveBack(1))

                pool.inUse.size mustEqual maxObjects - 1
                pool.queued.size mustEqual 0
                pool.availables.size mustEqual 1
            }

            "take maxObjects and receive one invalid back" in new Context {
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

                "one take queued" in new Context {
                    takeAndWait(maxObjects)
                    pool.take

                    pool.inUse.size mustEqual maxObjects
                    pool.queued.size mustEqual 1
                    pool.availables.size mustEqual 0
                }

                "one take queued and receive one item back" in new Context {
                    takeAndWait(maxObjects)
                    val taking = pool.take

                    await(pool.giveBack(1))

                    await(taking) mustEqual 1
                    pool.inUse.size mustEqual maxObjects
                    pool.queued.size mustEqual 0
                    pool.availables.size mustEqual 0
                }

                "one take queued and receive one invalid item back" in new Context {
                    takeAndWait(maxObjects)
                    // TODO 'taking' future is never completed
                    val taking = pool.take
                    factory.reject += 1
                    await(pool.giveBack(1)) must throwA[IllegalStateException]

                    pool.inUse.size mustEqual maxObjects - 1
                    pool.queued.size mustEqual 1
                    pool.availables.size mustEqual 0
                }

                "maxQueueSize takes queued" in new Context {
                    takeAndWait(maxObjects)
                    for (_ <- 0 until maxQueueSize)
                        pool.take

                    println(s"inUse: ${pool.inUse.size}")
                    println(s"queued: ${pool.queued.size}")
                    println(s"availables: ${pool.availables.size}")
                    Thread.sleep(100)
                    pool.inUse.size mustEqual maxObjects
                    pool.queued.size mustEqual maxQueueSize
                    pool.availables.size mustEqual 0
                }

                "maxQueueSize takes queued and receive one back" in new Context {
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

                "maxQueueSize takes queued and receive one invalid back" in new Context {
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

                "start to reject takes" in new Context {
                    takeAndWait(maxObjects)
                    for (_ <- 0 until maxQueueSize)
                        pool.take

                    await(pool.take) must throwA[PoolExhaustedException]

                    pool.inUse.size mustEqual maxObjects
                    pool.queued.size mustEqual maxQueueSize
                    pool.availables.size mustEqual 0
                }

                "receive an object back" in new Context {
                    takeAndWait(maxObjects)
                    for (_ <- 0 until maxQueueSize)
                        pool.take

                    await(pool.giveBack(1))

                    pool.inUse.size mustEqual maxObjects
                    pool.queued.size mustEqual maxQueueSize - 1
                    pool.availables.size mustEqual 0
                }

                "receive an invalid object back" in new Context {
                    takeAndWait(maxObjects)
                    for (_ <- 0 until maxQueueSize)
                        pool.take

                    factory.reject += 1
                    await(pool.giveBack(1)) must throwA[IllegalStateException]

                    pool.inUse.size mustEqual maxObjects - 1
                    pool.queued.size mustEqual maxQueueSize
                    pool.availables.size mustEqual 0
                }

                "receive maxQueueSize objects back" in new Context {
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

                "receive maxQueueSize invalid objects back" in new Context {
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

                "receive maxQueueSize + 1 object back" in new Context {
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

                "receive maxQueueSize + 1 invalid object back" in new Context {
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

    "gives back the connection to the original pool" in new Context {
        val executor = Executors.newFixedThreadPool(20)
        implicit val context = ExecutionContext.fromExecutor(executor)

        val takes =
            for (_ <- 0 until 30) yield {
                Future().flatMap(_ => pool.take)
            }
        val takesAndReturns =
            Future.sequence(takes).flatMap { items =>
                Future.sequence(items.map(pool.giveBack))
            }

        await(takesAndReturns)

        executor.shutdown
        pool.inUse.size mustEqual 0
        pool.queued.size mustEqual 0
        pool.availables.size mustEqual 30
    }

    private def await[T](future: Future[T]) =
        Await.result(future, Duration.Inf)
}
