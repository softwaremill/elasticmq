package org.elasticmq.actor.queue

import org.elasticmq.NeverReceived
import org.joda.time.DateTime

import scala.collection.mutable
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class InternalMessageSpec extends AnyFunSuite with Matchers {
  test("Internal FIFO message should be ordered by their creation time") {
    val freezedDateTime = new DateTime()

    val first = InternalMessage(
      id = "id",
      deliveryReceipts = mutable.Buffer.empty,
      nextDelivery = 0L,
      content = "content",
      messageAttributes = Map.empty,
      created = freezedDateTime,
      orderIndex = 100,
      firstReceive = NeverReceived,
      receiveCount = 0,
      isFifo = true,
      messageGroupId = None,
      messageDeduplicationId = None,
      tracingId = None
    )

    val second = first.copy(
      created = freezedDateTime.plusMillis(1)
    )

    first.compareTo(second) shouldBe 1
  }

  test("Internal FIFO messages should be ordered by their orderIndex if they share the same creation time") {
    val freezedDateTime = new DateTime()

    val first = InternalMessage(
      id = "id",
      deliveryReceipts = mutable.Buffer.empty,
      nextDelivery = 0L,
      content = "content",
      messageAttributes = Map.empty,
      created = freezedDateTime,
      orderIndex = 100,
      firstReceive = NeverReceived,
      receiveCount = 0,
      isFifo = true,
      messageGroupId = None,
      messageDeduplicationId = None,
      tracingId = None
    )

    val second = first.copy(
      orderIndex = first.orderIndex + 1
    )

    first.compareTo(second) shouldBe 1
  }
}
