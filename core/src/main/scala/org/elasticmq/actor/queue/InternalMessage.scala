package org.elasticmq.actor.queue

import org.elasticmq._
import org.elasticmq.util.NowProvider

import java.time.OffsetDateTime
import java.util.UUID
import scala.collection.mutable

case class InternalMessage(
    id: String,
    deliveryReceipts: mutable.Buffer[String],
    var nextDelivery: Long,
    content: String,
    messageAttributes: Map[String, MessageAttribute],
    messageSystemAttributes: mutable.HashMap[String, MessageAttribute],
    created: OffsetDateTime,
    orderIndex: Int,
    var firstReceive: Received,
    var receiveCount: Int,
    isFifo: Boolean,
    messageGroupId: Option[String],
    messageDeduplicationId: Option[DeduplicationId],
    tracingId: Option[TracingId],
    sequenceNumber: Option[String]
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
    * @param nextDeliveryMillis
    *   When this message should become available for its next delivery
    */
  def trackDelivery(nextDeliveryMillis: MillisNextDelivery)(implicit nowProvider: NowProvider): Unit = {
    deliveryReceipts += DeliveryReceipt.generate(MessageId(id)).receipt
    nextDelivery = nextDeliveryMillis.millis
    receiveCount += 1

    if (firstReceive == NeverReceived) {
      firstReceive = OnDateTimeReceived(nowProvider.now)
    }
  }

  def toMessageData =
    MessageData(
      MessageId(id),
      deliveryReceipts.lastOption.map(DeliveryReceipt(_)),
      content,
      messageAttributes,
      messageSystemAttributes.to(Map),
      MillisNextDelivery(nextDelivery),
      created,
      MessageStatistics(firstReceive, receiveCount),
      messageGroupId,
      messageDeduplicationId,
      tracingId,
      sequenceNumber
    )

  def toNewMessageData =
    NewMessageData(
      Some(MessageId(id)),
      content,
      messageAttributes,
      messageSystemAttributes.to(Map),
      MillisNextDelivery(nextDelivery),
      messageGroupId,
      messageDeduplicationId,
      orderIndex,
      tracingId,
      sequenceNumber
    )

  def deliverable(deliveryTime: Long): Boolean = nextDelivery <= deliveryTime
}

object InternalMessage {
  def from(newMessageData: NewMessageData, queueData: QueueData): InternalMessage = {

    val now = System.currentTimeMillis()
    new InternalMessage(
      newMessageData.id.getOrElse(generateId()).id,
      mutable.Buffer.empty,
      newMessageData.nextDelivery.toMillis(now, queueData.delay.toMillis).millis,
      newMessageData.content,
      newMessageData.messageAttributes,
      newMessageData.messageSystemAttributes.to(mutable.HashMap),
      OffsetDateTime.now(),
      newMessageData.orderIndex,
      NeverReceived,
      0,
      queueData.isFifo,
      newMessageData.messageGroupId,
      newMessageData.messageDeduplicationId,
      newMessageData.tracingId,
      newMessageData.sequenceNumber
    )
  }

  private def generateId() = MessageId(UUID.randomUUID().toString)
}
