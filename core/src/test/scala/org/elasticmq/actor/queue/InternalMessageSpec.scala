package org.elasticmq.actor.queue

import org.elasticmq.NeverReceived
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import scala.collection.mutable

class InternalMessageSpec extends AnyFunSuite with Matchers {
  test("Internal FIFO message should be ordered by their creation time") {
    val freezedDateTime = OffsetDateTime.now()

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
      tracingId = None,
      sequenceNumber = None,
      deadLetterSourceQueueName = None
    )

    val second = first.copy(
      created = freezedDateTime.plus(1, ChronoUnit.MILLIS)
    )

    first.compareTo(second) shouldBe 1
  }

  test("Internal FIFO messages should be ordered by their orderIndex if they share the same creation time") {
    val freezedDateTime = OffsetDateTime.now()

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
      tracingId = None,
      sequenceNumber = None,
      deadLetterSourceQueueName = None
    )

    val second = first.copy(
      orderIndex = first.orderIndex + 1
    )

    first.compareTo(second) shouldBe 1
  }
}
