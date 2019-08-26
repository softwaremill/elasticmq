package org.elasticmq.actor.queue

import org.elasticmq.NeverReceived
import org.elasticmq.actor.queue.InternalMessage.PreciseDateTime
import org.scalatest.{FunSuite, Matchers}

import scala.collection.mutable

class InternalMessageSpec extends FunSuite with Matchers {
  test("PreciseDateTime should be comparable") {
    PreciseDateTime().compareTo(PreciseDateTime()) shouldBe -1
  }

  test("PreciseDateTime's created at the same instant should be comparable using a more granular precision") {
    val first = PreciseDateTime()
    val second = first.copy(
      nanoSeconds = first.nanoSeconds + 1
    )

    first.compareTo(second) shouldBe -1
  }

  test("Internal FIFO message should be ordered by their creation time") {
    val first = InternalMessage(
      id = "id",
      deliveryReceipts = mutable.Buffer.empty,
      nextDelivery = 0L,
      content = "content",
      messageAttributes = Map.empty,
      created = PreciseDateTime(),
      firstReceive = NeverReceived,
      receiveCount = 0,
      isFifo = true,
      messageGroupId = None,
      messageDeduplicationId = None
    )

    val second = first.copy(
      created = PreciseDateTime()
    )

    first.compareTo(second) shouldBe -1
  }
}
