package org.elasticmq.actor

import org.elasticmq._
import org.elasticmq.actor.reply._
import org.elasticmq.actor.test._
import org.elasticmq.msg._
import org.joda.time.DateTime
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

abstract class DeduplicationTimerTest
  extends ActorTest
    with Matchers
    with QueueManagerForEachTest
    with DataCreationHelpers
    with EitherValues {

  test(
    "FIFO messages should be deleted from history if they were created more than 5 minutes ago and it should be possible to add new messages to queue with same deduplication ID"
  ) {
    val (fifoQueue, firstLookup) = Await.result(
      for {
        maybeFifoQueue <- queueManagerActor ? CreateQueue(
          createQueueData("q1.fifo", MillisVisibilityTimeout(1L), isFifo = true, hasContentBasedDeduplication = true)
        )
        fifoQueue = maybeFifoQueue.getOrElse(fail("Could not create Queue"))
        _ <- fifoQueue ? SendMessage(createMessage("id1", "body1", DeduplicationId("dedup")))
        _ <- fifoQueue ? SendMessage(createMessage("id2", "body2", DeduplicationId("dedup2")))
        firstLookup <- fifoQueue ? ReceiveMessages(DefaultVisibilityTimeout, 10, None, None)
      } yield (fifoQueue, firstLookup),
      2.second
    )
    nowProvider.mutableNowMillis.set(DateTime.now().plusMinutes(5).plusSeconds(1).getMillis)

    // Timer is scheduled to run each second, to eliminate random time errors we are waiting a little bit longer
    Thread.sleep(1500)

    val secondLookupAfterFiveMinutes = Await.result(
      for {
        _ <- fifoQueue ? SendMessage(createMessage("id3", "body1", DeduplicationId("dedup")))
        _ <- fifoQueue ? SendMessage(createMessage("id4", "body2", DeduplicationId("dedup2")))
        secondLookup <- fifoQueue ? ReceiveMessages(DefaultVisibilityTimeout, 10, None, None)
      } yield secondLookup,
      2.second
    )

    firstLookup.map(_.id) shouldBe List(MessageId("id1"), MessageId("id2"))
    secondLookupAfterFiveMinutes
      .map(_.id) shouldBe List(MessageId("id1"), MessageId("id2"), MessageId("id3"), MessageId("id4"))

  }

  private def createMessage(id: String, body: String, deduplicationId: DeduplicationId) =
    createNewMessageData(
      id = id,
      content = body,
      messageAttributes = Map(),
      nextDelivery = MillisNextDelivery(0L),
      messageGroupId = Some("g1"),
      messageDeduplicationId = Some(deduplicationId)
    )
}

class DeduplicationTimerTestWithInMemoryQueues extends DeduplicationTimerTest with MessagePersistenceDisabledConfig

class DeduplicationTimerTestWithPersistenceQueues extends DeduplicationTimerTest with MessagePersistenceEnabledConfig