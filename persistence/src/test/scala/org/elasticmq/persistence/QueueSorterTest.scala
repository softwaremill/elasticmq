package org.elasticmq.persistence

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class QueueSorterTest extends AnyFunSuite with Matchers {
  test("sorts an empty list") {
    val sortedQueues = QueueSorter.sortCreateQueues(List())
    sortedQueues should have size (0)
  }

  test("sorts one queue") {
    val sortedQueues = QueueSorter.sortCreateQueues(List(CreateQueueMetadata("queue1", None, None, None, None, false, false)))
    sortedQueues should have size (1)
    sortedQueues.head.name should be("queue1")
  }

  test("sorts queue and dead letters queue") {
    val sortedQueues = QueueSorter.sortCreateQueues(
      List(
        CreateQueueMetadata("queue1", None, None, None, Some(DeadLettersQueue("deadletters", 1)), false, false),
        CreateQueueMetadata("deadletters", None, None, None, None, false, false)
      )
    )
    sortedQueues should have size (2)
    sortedQueues.head.name should be("deadletters")
  }

  test("sorts queue and copyTo/moveTo queues") {
    val sortedQueues = QueueSorter.sortCreateQueues(
      List(
        CreateQueueMetadata("queue1", None, None, None, None, false, false, None, Some("redirect")),
        CreateQueueMetadata("redirect", None, None, None, None, false, false, Some("audit"), None),
        CreateQueueMetadata("deadletters", None, None, None, None, false, false),
        CreateQueueMetadata("audit", None, None, None, Some(DeadLettersQueue("deadletters", 1)), false, false, None, None)
      )
    )
    sortedQueues.map(_.name) should be(
      List("deadletters", "audit", "redirect", "queue1")
    )
  }

  test("throws exception for circular graphs") {
    an[IllegalArgumentException] should be thrownBy QueueSorter.sortCreateQueues(
      List(
        CreateQueueMetadata("queue1", None, None, None, Some(DeadLettersQueue("deadletters", 1)), false, false),
        CreateQueueMetadata("deadletters", None, None, None, Some(DeadLettersQueue("queue1", 1)), false, false)
      )
    )
  }

  test("sorts two queues that use the same dead letters queue") {
    val sortedQueues = QueueSorter.sortCreateQueues(
      List(
        CreateQueueMetadata("queue1", None, None, None, Some(DeadLettersQueue("deadletters", 1)), false, false),
        CreateQueueMetadata("deadletters", None, None, None, None, false, false),
        CreateQueueMetadata("queue2", None, None, None, Some(DeadLettersQueue("deadletters", 1)), false, false)
      )
    )
    sortedQueues should have size (3)
    sortedQueues.head.name should be("deadletters")
    sortedQueues.tail.map(_.name) should contain only ("queue1", "queue2")
  }

  test("sorts chained dead letters queues") {
    val sortedQueues = QueueSorter.sortCreateQueues(
      List(
        CreateQueueMetadata("queue1", None, None, None, Some(DeadLettersQueue("deadletters1", 1)), false, false),
        CreateQueueMetadata("deadletters1", None, None, None, Some(DeadLettersQueue("deadletters2", 1)), false, false),
        CreateQueueMetadata("deadletters2", None, None, None, None, false, false),
        CreateQueueMetadata("queue2", None, None, None, Some(DeadLettersQueue("deadletters1", 1)), false, false)
      )
    )
    sortedQueues should have size (4)
    sortedQueues.take(2).map(_.name) should contain inOrderOnly ("deadletters2", "deadletters1")
    sortedQueues.drop(2).map(_.name) should contain only ("queue1", "queue2")
  }
}
