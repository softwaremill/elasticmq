package org.elasticmq.actor.queue

import java.util.UUID

import akka.actor.ActorRef
import org.elasticmq.{MessageId, MillisNextDelivery, _}
import org.elasticmq.util.NowProvider
import org.joda.time.DateTime

import scala.collection.mutable

trait QueueActorStorage {
  def nowProvider: NowProvider

  def initialQueueData: QueueData

  def deadLettersActorRef: Option[ActorRef]

  var queueData = initialQueueData
  var messageQueue = mutable.PriorityQueue[InternalMessage]()
  val messagesById = mutable.HashMap[String, InternalMessage]()
  val deadLettersQueueActor = deadLettersActorRef

  case class InternalMessage(id: String,
                             var deliveryReceipt: Option[String],
                             var nextDelivery: Long,
                             content: String,
                             messageAttributes: Map[String, MessageAttribute],
                             created: DateTime,
                             var firstReceive: Received,
                             var receiveCount: Int)
      extends Comparable[InternalMessage] {

    // Priority queues have biggest elements first
    def compareTo(other: InternalMessage) =
      -nextDelivery.compareTo(other.nextDelivery)

    def toMessageData =
      MessageData(
        MessageId(id),
        deliveryReceipt.map(DeliveryReceipt(_)),
        content,
        messageAttributes,
        MillisNextDelivery(nextDelivery),
        created,
        MessageStatistics(firstReceive, receiveCount)
      )

    def toNewMessageData =
      NewMessageData(Some(MessageId(id)), content, messageAttributes, MillisNextDelivery(nextDelivery))

    def deliverable(deliveryTime: Long): Boolean = nextDelivery <= deliveryTime
  }

  object InternalMessage {
    def from(messageData: MessageData) =
      InternalMessage(
        messageData.id.id,
        messageData.deliveryReceipt.map(_.receipt),
        messageData.nextDelivery.millis,
        messageData.content,
        messageData.messageAttributes,
        messageData.created,
        messageData.statistics.approximateFirstReceive,
        messageData.statistics.approximateReceiveCount
      )

    def from(newMessageData: NewMessageData) =
      InternalMessage(
        newMessageData.id.getOrElse(generateId()).id,
        None,
        newMessageData.nextDelivery
          .toMillis(nowProvider.nowMillis, queueData.delay.getMillis)
          .millis,
        newMessageData.content,
        newMessageData.messageAttributes,
        nowProvider.now,
        NeverReceived,
        0
      )
  }

  private def generateId() = MessageId(UUID.randomUUID().toString)
}
