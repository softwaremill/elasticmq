package org.elasticmq.actor.queue

import scala.collection.mutable
import org.elasticmq.NeverReceived
import org.elasticmq.actor.queue.ReceiveRequestAttemptCache.ReceiveFailure.Invalid
import org.elasticmq.actor.test.MutableNowProvider
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ReceiveRequestAttemptCacheTest extends AnyFunSuite with Matchers {

  test("should retrieve message by attempt id or return the appropriate failure") {
    val cache = new ReceiveRequestAttemptCache

    implicit val nowProvider = new MutableNowProvider
    nowProvider.mutableNowMillis.set(1L)

    val attemptId1 = "attempt-1"
    val attemptId2 = "attempt-2"
    val msg1 = InternalMessage(
      "id-1",
      mutable.Buffer.empty,
      1L,
      "content",
      Map.empty,
      nowProvider.now,
      orderIndex = 0,
      NeverReceived,
      receiveCount = 0,
      isFifo = false,
      messageGroupId = None,
      messageDeduplicationId = None
    )
    val msg2 = msg1.copy(id = "id-2")
    val msg3 = msg1.copy(id = "id-3")

    val messageQueue = MessageQueue(isFifo = false)
    messageQueue += msg1
    messageQueue += msg2
    messageQueue += msg3

    cache.add(attemptId1, List(msg1, msg2))
    cache.add(attemptId2, List(msg3))

    // Verify the happy path
    cache.get(attemptId1, messageQueue).right.get should be(Some(List(msg1, msg2)))
    cache.get(attemptId2, messageQueue).right.get should be(Some(List(msg3)))
    cache.get("unknown-attempt", messageQueue).right.get should be(empty)

    // Using the attempt id from another queue
    messageQueue.remove(msg1.id)
    messageQueue.remove(msg3.id)
    cache.get(attemptId1, messageQueue).left.get should be(Invalid)

    // Verify expirations (5 minutes later + some change)
    nowProvider.mutableNowMillis.set(1L + 5 * 60 * 1000 + 2000)
    cache.get(attemptId1, messageQueue).left.get should be(ReceiveRequestAttemptCache.ReceiveFailure.Expired)
    cache.get(attemptId2, messageQueue).left.get should be(ReceiveRequestAttemptCache.ReceiveFailure.Expired)
  }

  test("adding an attempt should clean out any old attempts") {
    // Given
    val cache = new ReceiveRequestAttemptCache
    implicit val nowProvider = new MutableNowProvider
    nowProvider.mutableNowMillis.set(1L)
    val attemptId1 = "attempt-1"
    val attemptId2 = "attempt-2"
    val msg1 = InternalMessage(
      "id-1",
      mutable.Buffer.empty,
      1L,
      "content",
      Map.empty,
      nowProvider.now,
      orderIndex = 0,
      NeverReceived,
      receiveCount = 0,
      isFifo = false,
      messageGroupId = None,
      messageDeduplicationId = None
    )
    val msg2 = msg1.copy(id = "id-2")
    val messageQueue = MessageQueue(isFifo = false)
    messageQueue += msg1
    messageQueue += msg2
    cache.add(attemptId1, List(msg1))

    // Sanity-check the attempt is still in the cache
    cache.get(attemptId1, messageQueue).right.get should be(defined)

    // 15 minutes pass, the attempt should be removed from the cache to prevent OOMs
    nowProvider.mutableNowMillis.set(15 * 60 * 1000 + 2)
    cache.add(attemptId2, List(msg2))
    cache.get(attemptId1, messageQueue).right.get should be(empty)
  }
}
