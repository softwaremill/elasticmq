package org.elasticmq.actor

import org.elasticmq.actor.reply._
import org.elasticmq._
import org.elasticmq.message._
import org.elasticmq.actor.test.{DataCreationHelpers, QueueManagerForEachTest, ActorTest}
import org.joda.time.{Duration, DateTime}
import org.elasticmq.data.QueueData

class QueueActorMsgOpsTest extends ActorTest with QueueManagerForEachTest with DataCreationHelpers {

  waitTest("queue modified and created dates should be stored") {
    // Given
    val created = new DateTime(1216168602L)
    val lastModified = new DateTime(1316168602L)

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(QueueData("q1", MillisVisibilityTimeout(1L), Duration.ZERO, created, lastModified))

      // When
      queueData <- queueActor ? GetQueueData()
    } yield {
      // Then
      queueData should be (QueueData("q1", MillisVisibilityTimeout(1L), Duration.ZERO, created, lastModified))
    }
  }

  waitTest("updating a queue") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val q1Modified = createQueueData("q1", MillisVisibilityTimeout(100L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)

      // When
      _ <- queueActor ? UpdateQueueDefaultVisibilityTimeout(q1Modified.defaultVisibilityTimeout)
      queueData <- queueActor ? GetQueueData()
    } yield {
      // Then
      queueData should be (q1Modified)
    }
  }

  waitTest("queue statistics without messages") {
    // Given
    val queue = createQueueData("q1", MillisVisibilityTimeout(1L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(queue)

      // When
      stats <- queueActor ? GetQueueStatistics(123L)
    } yield {
      // Then
      stats should be (QueueStatistics(0L, 0L, 0L))
    }
  }

  waitTest("queue statistics with messages") {
    // Given
    val queue = createQueueData("q1", MillisVisibilityTimeout(1L))

    val m1 = createMessageData("m1", "123", MillisNextDelivery(120L))
    val m2 = createMessageData("m2", "123", MillisNextDelivery(121L))
    val m3 = createMessageData("m3", "123", MillisNextDelivery(122L))
    val m4 = createMessageData("m4", "123", MillisNextDelivery(123L))

    val m5 = createMessageData("m5", "123", MillisNextDelivery(120L))
    val m6 = createMessageData("m6", "123", MillisNextDelivery(120L))

    val m7 = createMessageData("m7", "123", MillisNextDelivery(127L))
    val m8 = createMessageData("m8", "123", MillisNextDelivery(128L))
    val m9 = createMessageData("m9", "123", MillisNextDelivery(129L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(queue)

      // Invisible messages - received
      _ <- queueActor ? SendMessage(m1)
      _ <- queueActor ? ReceiveMessage(123L, MillisNextDelivery(130L))
      _ <- queueActor ? SendMessage(m2)
      _ <- queueActor ? ReceiveMessage(123L, MillisNextDelivery(130L))
      _ <- queueActor ? SendMessage(m3)
      _ <- queueActor ? ReceiveMessage(123L, MillisNextDelivery(130L))
      _ <- queueActor ? SendMessage(m4)
      _ <- queueActor ? ReceiveMessage(123L, MillisNextDelivery(130L))

      // Visible messages
      _ <- queueActor ? SendMessage(m5)
      _ <- queueActor ? SendMessage(m6)

      // Delayed messages - never yet received
      _ <- queueActor ? SendMessage(m7)
      _ <- queueActor ? SendMessage(m8)
      _ <- queueActor ? SendMessage(m9)

      // When
      stats <- queueActor ? GetQueueStatistics(123L)
    } yield {
      // Then
      stats should be (QueueStatistics(2L, 4L, 3L))
    }
  }
}
