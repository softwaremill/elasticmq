package org.elasticmq

import org.elasticmq.actor.queue.InternalMessage
import org.elasticmq.util.NowProvider
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.OffsetDateTime
import scala.collection.mutable

class FifoDeduplicationIdsHistoryTest extends AnyFunSuite with Matchers {

  test("Should store added deduplication IDs") {
    val history = FifoDeduplicationIdsHistory.newHistory()
    val updatedHistory = history
      .addNew(newInternalMessage(Some(DeduplicationId("1")), OffsetDateTime.now()))
      .addNew(newInternalMessage(Some(DeduplicationId("2")), OffsetDateTime.now()))
      .addNew(newInternalMessage(Some(DeduplicationId("3")), OffsetDateTime.now()))
      .addNew(newInternalMessage(Some(DeduplicationId("4")), OffsetDateTime.now()))

    updatedHistory.messagesByDeduplicationId.keys shouldBe Set(
      DeduplicationId("1"),
      DeduplicationId("2"),
      DeduplicationId("3"),
      DeduplicationId("4")
    )
    updatedHistory.deduplicationIdsByCreationDate
      .map(_.id) shouldBe List(DeduplicationId("1"), DeduplicationId("2"), DeduplicationId("3"), DeduplicationId("4"))
  }

  test("History should not override given deduplication ID entry if such one already exists") {
    val history = FifoDeduplicationIdsHistory.newHistory()
    val OffsetDateTimeInPast = OffsetDateTime.now().minusMinutes(10)
    val updatedHistory = history
      .addNew(newInternalMessage(Some(DeduplicationId("1")), OffsetDateTimeInPast))
      .addNew(newInternalMessage(Some(DeduplicationId("1")), OffsetDateTime.now()))

    updatedHistory.messagesByDeduplicationId.keys shouldBe Set(
      DeduplicationId("1")
    )
    updatedHistory.deduplicationIdsByCreationDate shouldBe List(
      DeduplicationIdWithCreationDate(DeduplicationId("1"), OffsetDateTimeInPast)
    )
  }

  test("History should show if given deduplication ID was already used or not") {
    val history = FifoDeduplicationIdsHistory.newHistory()
    val updatedHistory = history
      .addNew(newInternalMessage(Some(DeduplicationId("1")), OffsetDateTime.now()))
      .addNew(newInternalMessage(Some(DeduplicationId("2")), OffsetDateTime.now()))
      .addNew(newInternalMessage(Some(DeduplicationId("3")), OffsetDateTime.now()))
      .addNew(newInternalMessage(Some(DeduplicationId("4")), OffsetDateTime.now()))

    updatedHistory.wasRegistered(Some(DeduplicationId("1"))) should be(defined)
    updatedHistory.wasRegistered(Some(DeduplicationId("4"))) should be(defined)
    updatedHistory.wasRegistered(Some(DeduplicationId("7"))) should not be defined
    updatedHistory.wasRegistered(None) should not be defined
  }

  test("History should erase all entries that were created 5 or more minutes ago") {
    val history = FifoDeduplicationIdsHistory.newHistory()
    val now = OffsetDateTime.now()
    val updatedHistory = history
      .addNew(newInternalMessage(Some(DeduplicationId("1")), now.minusMinutes(20)))
      .addNew(newInternalMessage(Some(DeduplicationId("2")), now.minusMinutes(10)))
      .addNew(newInternalMessage(Some(DeduplicationId("3")), now.minusMinutes(5).minusSeconds(1)))
      .addNew(newInternalMessage(Some(DeduplicationId("4")), now.minusMinutes(4).minusSeconds(59)))
      .addNew(newInternalMessage(Some(DeduplicationId("5")), now))
      .cleanOutdatedMessages(new NowProvider)

    updatedHistory.messagesByDeduplicationId.keys shouldBe Set(DeduplicationId("4"), DeduplicationId("5"))
    updatedHistory.deduplicationIdsByCreationDate shouldBe List(
      DeduplicationIdWithCreationDate(DeduplicationId("4"), now.minusMinutes(4).minusSeconds(59)),
      DeduplicationIdWithCreationDate(DeduplicationId("5"), now)
    )
  }

  test("Cleaning outdated IDs should stop at first ID which was created in last 5 minutes") {
    val history = FifoDeduplicationIdsHistory.newHistory()
    val now = OffsetDateTime.now()
    val updatedHistory = history
      .addNew(newInternalMessage(Some(DeduplicationId("1")), now.minusMinutes(20)))
      .addNew(newInternalMessage(Some(DeduplicationId("2")), now.minusMinutes(4)))
      .addNew(newInternalMessage(Some(DeduplicationId("3")), now.minusMinutes(5)))
      .addNew(newInternalMessage(Some(DeduplicationId("4")), now.minusMinutes(4).minusSeconds(59)))
      .addNew(newInternalMessage(Some(DeduplicationId("5")), now))
      .cleanOutdatedMessages(new NowProvider)

    updatedHistory.messagesByDeduplicationId.keys shouldBe Set(
      DeduplicationId("2"),
      DeduplicationId("3"),
      DeduplicationId("4"),
      DeduplicationId("5")
    )
    updatedHistory.deduplicationIdsByCreationDate shouldBe List(
      DeduplicationIdWithCreationDate(DeduplicationId("2"), now.minusMinutes(4)),
      DeduplicationIdWithCreationDate(DeduplicationId("3"), now.minusMinutes(5)),
      DeduplicationIdWithCreationDate(DeduplicationId("4"), now.minusMinutes(4).minusSeconds(59)),
      DeduplicationIdWithCreationDate(DeduplicationId("5"), now)
    )
  }

  def newInternalMessage(maybeDeduplicationId: Option[DeduplicationId], created: OffsetDateTime): InternalMessage =
    InternalMessage(
      id = "1",
      deliveryReceipts = mutable.Buffer.empty,
      nextDelivery = 100L,
      content = "",
      messageAttributes = Map.empty,
      messageSystemAttributes = Map.empty,
      created = created,
      orderIndex = 0,
      firstReceive = NeverReceived,
      receiveCount = 0,
      isFifo = true,
      messageGroupId = None,
      messageDeduplicationId = maybeDeduplicationId,
      tracingId = None,
      sequenceNumber = None
    )

}
