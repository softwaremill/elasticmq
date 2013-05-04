package org.elasticmq.actor

import akka.actor.Props
import org.elasticmq.actor.reply._
import org.elasticmq.message.{ListQueues, CreateQueue}
import org.elasticmq.data.QueueData
import org.elasticmq.MillisVisibilityTimeout
import org.joda.time.{DateTime, Duration}

class QueueManagerActorTest extends ActorTest {
  it should "create a queue" in {
    // Given
    val queueManager = system.actorOf(Props[QueueManagerActor])
    val queueData = QueueData("q1", MillisVisibilityTimeout(1000L), Duration.ZERO, new DateTime(), new DateTime())

    // When
    val result = waitFor(for {
      _ <- queueManager ? CreateQueue(queueData)
      queueNames <- queueManager ? ListQueues()
    } yield queueNames)

    // Then
    result.toList should be (List("q1"))
  }
}
