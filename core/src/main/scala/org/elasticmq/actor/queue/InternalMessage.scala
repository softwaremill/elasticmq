package org.elasticmq.actor.queue

import java.util.UUID

import org.elasticmq._
import org.elasticmq.util.NowProvider
import org.joda.time.DateTime

import scala.collection.mutable

case class InternalMessage(
    id: String,
    deliveryReceipts: mutable.Buffer[String],
    var nextDelivery: Long,
    content: String,
    messageAttributes: Map[String, MessageAttribute],
    created: DateTime,
    orderIndex: Int,
    var firstReceive: Received,
    var receiveCount: Int,
    isFifo: Boolean,
    messageGroupId: Option[String],
    messageDeduplicationId: Option[DeduplicationId],
    tracingId: Option[TracingId]
) extends Comparable[InternalMessage] {

  // Priority queues have biggest elements first
  override def compareTo(other: InternalMessage): Int = {
    if (isFifo) {
      // FIFO messages should be ordered on when they were written to the queue
      val comp = -created.compareTo(other.created)
      if (comp == 0) {
        -orderIndex.compareTo(other.orderIndex)
      } else comp
    } else {
      // Plain messages are technically not ordered, but AWS seems to offer a loose, non-guaranteed "next delivery"
      // ordering
      -nextDelivery.compareTo(other.nextDelivery)
    }
  }

  /** Keep track of delivering this message to a client
    *
    * @param nextDeliveryMillis    When this message should become available for its next delivery
    */
  def trackDelivery(nextDeliveryMillis: MillisNextDelivery)(implicit nowProvider: NowProvider): Unit = {
    deliveryReceipts += DeliveryReceipt.generate(MessageId(id)).receipt
    nextDelivery = nextDeliveryMillis.millis
    receiveCount += 1

    if (firstReceive == NeverReceived) {
      firstReceive = OnDateTimeReceived(new DateTime(nowProvider.nowMillis))
    }
  }

  def toMessageData =
    MessageData(
      MessageId(id),
      deliveryReceipts.lastOption.map(DeliveryReceipt(_)),
      content,
      messageAttributes,
      MillisNextDelivery(nextDelivery),
      created,
      MessageStatistics(firstReceive, receiveCount),
      messageGroupId,
      messageDeduplicationId,
      tracingId
    )

  def toNewMessageData =
    NewMessageData(
      Some(MessageId(id)),
      content,
      messageAttributes,
      MillisNextDelivery(nextDelivery),
      messageGroupId,
      messageDeduplicationId,
      orderIndex,
      tracingId
    )

  def deliverable(deliveryTime: Long): Boolean = nextDelivery <= deliveryTime
}

object InternalMessage {

  def from(newMessageData: NewMessageData, queueData: QueueData, n: BigInt): InternalMessage = {
    val attributes = newMessageData
      .messageAttributes
      .updatedWith("SequenceNumber")(
      _ => if (queueData.isFifo) Some(StringMessageAttribute(n.toString())) else None
    )

    val now = System.currentTimeMillis()
    new InternalMessage(
      newMessageData.id.getOrElse(generateId()).id,
      mutable.Buffer.empty,
      newMessageData.nextDelivery.toMillis(now, queueData.delay.getMillis).millis,
      newMessageData.content,
      attributes,
      new DateTime(),
      newMessageData.orderIndex,
      NeverReceived,
      0,
      queueData.isFifo,
      newMessageData.messageGroupId,
      newMessageData.messageDeduplicationId,
      newMessageData.tracingId
    )
  }

  private def generateId() = MessageId(UUID.randomUUID().toString)
}
