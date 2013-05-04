package org.elasticmq.actor

import org.elasticmq.actor.reply._
import org.elasticmq.message.{ListQueues, CreateQueue}
import org.elasticmq.data.QueueData
import org.elasticmq.MillisVisibilityTimeout
import org.joda.time.{DateTime, Duration}

class QueueManagerActorTest extends ActorTest with QueueManagerForEachTest {
  test("create a queue") {
    // Given
    val queueData = QueueData("q1", MillisVisibilityTimeout(1000L), Duration.ZERO, new DateTime(), new DateTime())

    // When
    val result = waitFor(for {
      _ <- queueManagerActor ? CreateQueue(queueData)
      queueNames <- queueManagerActor ? ListQueues()
    } yield queueNames)

    // Then
    result.toList should be (List("q1"))
  }
}
