package org.elasticmq.server

import org.elasticmq.server.config.{CreateQueue, DeadLettersQueue}
import org.scalatest.{FunSuite, Matchers}

class QueueSorterTest extends FunSuite with Matchers {
  test("sorts an empty list") {
    val sortedQueues = QueueSorter.sortCreateQueues(List())
    sortedQueues should have size(0)
  }

  test("sorts one queue") {
    val sortedQueues = QueueSorter.sortCreateQueues(List(CreateQueue("queue1", None, None, None, None)))
    sortedQueues should have size(1)
    sortedQueues.head.name should be ("queue1")
  }

  test("sorts queue and dead letters queue") {
    val sortedQueues = QueueSorter.sortCreateQueues(List(
      CreateQueue("queue1", None, None, None, Some(DeadLettersQueue("deadletters", 1))),
      CreateQueue("deadletters", None, None, None, None)
    ))
    sortedQueues should have size(2)
    sortedQueues.head.name should be ("deadletters")
  }

  test("throws exception for circular graphs") {
    an [IllegalArgumentException] should be thrownBy QueueSorter.sortCreateQueues(List(
      CreateQueue("queue1", None, None, None, Some(DeadLettersQueue("deadletters", 1))),
      CreateQueue("deadletters", None, None, None, Some(DeadLettersQueue("queue1", 1)))
    ))
  }

  test("sorts two queues that use the same dead letters queue") {
    val sortedQueues = QueueSorter.sortCreateQueues(List(
      CreateQueue("queue1", None, None, None, Some(DeadLettersQueue("deadletters", 1))),
      CreateQueue("deadletters", None, None, None, None),
      CreateQueue("queue2", None, None, None, Some(DeadLettersQueue("deadletters", 1)))
    ))
    sortedQueues should have size(3)
    sortedQueues.head.name should be ("deadletters")
    sortedQueues.tail.map(_.name) should contain only ("queue1", "queue2")
  }

  test("sorts chained dead letters queues") {
    val sortedQueues = QueueSorter.sortCreateQueues(List(
      CreateQueue("queue1", None, None, None, Some(DeadLettersQueue("deadletters1", 1))),
      CreateQueue("deadletters1", None, None, None, Some(DeadLettersQueue("deadletters2", 1))),
      CreateQueue("deadletters2", None, None, None, None),
      CreateQueue("queue2", None, None, None, Some(DeadLettersQueue("deadletters1", 1)))
    ))
    sortedQueues should have size(4)
    sortedQueues.take(2).map(_.name) should contain inOrderOnly ("deadletters2", "deadletters1")
    sortedQueues.drop(2).map(_.name) should contain only ("queue1", "queue2")
  }
}
