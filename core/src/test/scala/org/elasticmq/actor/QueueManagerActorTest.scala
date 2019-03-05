package org.elasticmq.actor

import org.elasticmq.actor.reply._
import org.elasticmq.msg.{DeleteQueue, LookupQueue, ListQueues, CreateQueue}
import org.elasticmq.MillisVisibilityTimeout
import org.elasticmq.actor.test.{DataCreationHelpers, QueueManagerForEachTest, ActorTest}

class QueueManagerActorTest extends ActorTest with QueueManagerForEachTest with DataCreationHelpers {

  test("non-existent queue should not be found") {
    for {
      // Given
      _ <- queueManagerActor ? CreateQueue(createQueueData("q1", MillisVisibilityTimeout(10L)))

      // When
      lookupResult <- queueManagerActor ? LookupQueue("q2")
    } yield {
      // Then
      lookupResult should be(None)
    }
  }

  test("after persisting a queue it should be found") {
    for {
      // Given
      _ <- queueManagerActor ? CreateQueue(createQueueData("q1", MillisVisibilityTimeout(1L)))
      _ <- queueManagerActor ? CreateQueue(createQueueData("q2", MillisVisibilityTimeout(2L)))
      _ <- queueManagerActor ? CreateQueue(createQueueData("q3", MillisVisibilityTimeout(3L)))

      // When
      lookupResult <- queueManagerActor ? LookupQueue("q2")
    } yield {
      // Then
      lookupResult should be('defined)
    }
  }

  test("queues should be deleted") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val q2 = createQueueData("q2", MillisVisibilityTimeout(2L))

    for {
      _ <- queueManagerActor ? CreateQueue(q1)
      _ <- queueManagerActor ? CreateQueue(q2)

      // When
      _ <- queueManagerActor ? DeleteQueue(q1.name)

      // Then
      r1 <- queueManagerActor ? LookupQueue(q1.name)
      r2 <- queueManagerActor ? LookupQueue(q2.name)
    } yield {
      r1 should be(None)
      r2 should be('defined)
    }
  }

  test("trying to create an existing queue should throw an exception") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))

    for {
      _ <- queueManagerActor ? CreateQueue(q1)

      // When & then
      result <- queueManagerActor ? CreateQueue(q1)
    } yield {
      result should be('left)
    }
  }

  test("listing queues") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val q2 = createQueueData("q2", MillisVisibilityTimeout(2L))

    for {
      _ <- queueManagerActor ? CreateQueue(q1)
      _ <- queueManagerActor ? CreateQueue(q2)

      // When
      queues <- queueManagerActor ? ListQueues()
    } yield {
      // Then
      queues.toSet should be(Set(q1.name, q2.name))
    }
  }
}
