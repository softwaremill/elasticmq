package org.elasticmq.persistence

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class QueueSorterTest extends AnyFunSuite with Matchers {
  test("sorts an empty list") {
    val sortedQueues = QueueSorter.sortCreateQueues(List())
    sortedQueues should have size (0)
  }

  test("sorts one queue") {
    val sortedQueues = QueueSorter.sortCreateQueues(List(CreateQueueMetadata("queue1")))
    sortedQueues should have size (1)
    sortedQueues.head.name should be("queue1")
  }

  test("sorts queue and dead letters queue") {
    val sortedQueues = QueueSorter.sortCreateQueues(
      List(
        CreateQueueMetadata("queue1", deadLettersQueue = Some(DeadLettersQueue("deadletters", 1))),
        CreateQueueMetadata("deadletters")
      )
    )
    sortedQueues should have size (2)
    sortedQueues.head.name should be("deadletters")
  }

  test("sorts queue and copyTo/moveTo queues") {
    val sortedQueues = QueueSorter.sortCreateQueues(
      List(
        CreateQueueMetadata("queue1", moveMessagesTo = Some("redirect")),
        CreateQueueMetadata("redirect", copyMessagesTo = Some("audit")),
        CreateQueueMetadata("deadletters"),
        CreateQueueMetadata("audit", deadLettersQueue = Some(DeadLettersQueue("deadletters", 1)))
      )
    )
    sortedQueues.map(_.name) should be(
      List("deadletters", "audit", "redirect", "queue1")
    )
  }

  test("throws exception for circular graphs") {
    an[IllegalArgumentException] should be thrownBy QueueSorter.sortCreateQueues(
      List(
        CreateQueueMetadata("queue1", deadLettersQueue = Some(DeadLettersQueue("deadletters", 1))),
        CreateQueueMetadata("deadletters", deadLettersQueue = Some(DeadLettersQueue("queue1", 1)))
      )
    )
  }

  test("sorts two queues that use the same dead letters queue") {
    val sortedQueues = QueueSorter.sortCreateQueues(
      List(
        CreateQueueMetadata("queue1", deadLettersQueue = Some(DeadLettersQueue("deadletters", 1))),
        CreateQueueMetadata("deadletters"),
        CreateQueueMetadata("queue2", deadLettersQueue = Some(DeadLettersQueue("deadletters", 1)))
      )
    )
    sortedQueues should have size (3)
    sortedQueues.head.name should be("deadletters")
    sortedQueues.tail.map(_.name) should contain only ("queue1", "queue2")
  }

  test("sorts chained dead letters queues") {
    val sortedQueues = QueueSorter.sortCreateQueues(
      List(
        CreateQueueMetadata("queue1", deadLettersQueue = Some(DeadLettersQueue("deadletters1", 1))),
        CreateQueueMetadata("deadletters1", deadLettersQueue = Some(DeadLettersQueue("deadletters2", 1))),
        CreateQueueMetadata("deadletters2"),
        CreateQueueMetadata("queue2", deadLettersQueue = Some(DeadLettersQueue("deadletters1", 1)))
      )
    )
    sortedQueues should have size (4)
    sortedQueues.take(2).map(_.name) should contain inOrderOnly ("deadletters2", "deadletters1")
    sortedQueues.drop(2).map(_.name) should contain only ("queue1", "queue2")
  }
}
