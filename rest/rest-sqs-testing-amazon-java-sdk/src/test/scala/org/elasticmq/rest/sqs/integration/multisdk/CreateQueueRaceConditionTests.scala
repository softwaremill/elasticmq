package org.elasticmq.rest.sqs.integration.multisdk

import org.elasticmq.rest.sqs.integration.client.VisibilityTimeoutAttributeName

import java.util.concurrent.Executors
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}

trait CreateQueueRaceConditionTests extends AmazonJavaMultiSdkTestBase {

  test("should create one queue and return its address for every request with the same name and metadata") {
    implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(100))

    val tasks = (0 until 100).map(_ => Future { testClient.createQueue("abc") })
    val results = Await.result(Future.sequence(tasks), Duration.Inf)

    results should be(Vector.fill(100)(Right("http://localhost:9321/123456789012/abc")))
  }

  test("should create one queue and return an error for every request with the same name and different metadata") {
    implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(100))

    val tasks = (0 until 100).map { index =>
      Future {
        testClient.createQueue(
          "abc",
          Map(VisibilityTimeoutAttributeName -> (10 + index).toString)
        )
      }
    }
    val results = Await.result(Future.sequence(tasks), Duration.Inf)

    val grouped = results.groupBy(_.isLeft)
    val rights = grouped.getOrElse(false, Nil)
    val lefts = grouped.getOrElse(true, Nil)
    rights.length should be(1)
    lefts.length should be(99)
  }
}
