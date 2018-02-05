package org.elasticmq.actor.queue

import java.util.UUID

import scala.annotation.tailrec
import scala.collection.mutable

import akka.actor.ActorRef
import org.elasticmq.{MessageId, MillisNextDelivery, _}
import org.elasticmq.util.NowProvider
import org.joda.time.DateTime

trait QueueActorStorage {
  def nowProvider: NowProvider

  def initialQueueData: QueueData

  def deadLettersActorRef: Option[ActorRef]

  class MessageQueue(
      isFifo: Boolean,
      messagesById: mutable.HashMap[String, InternalMessage] = mutable.HashMap.empty,
      messageQueue: mutable.PriorityQueue[InternalMessage] = mutable.PriorityQueue.empty,
      messagesbyMessageGroupId: mutable.HashMap[Option[String], mutable.PriorityQueue[InternalMessage]] = mutable.HashMap.empty) {

    def +=(m: InternalMessage): Unit = {
      messagesById += m.id -> m
      if (isFifo) {
        val groupMessages = messagesbyMessageGroupId.getOrElseUpdate(m.messageGroupId, mutable.PriorityQueue.empty)
        messagesbyMessageGroupId.put(m.messageGroupId, groupMessages += m)
      } else {
        messageQueue += m
      }
    }

    def byId: Map[String, InternalMessage] = messagesById.toMap

    def clear(): Unit = {
      messagesById.clear()
      messageQueue.clear()
      messagesbyMessageGroupId.clear()
    }

    def isEmpty: Boolean = if (isFifo) {
      messagesbyMessageGroupId.isEmpty
    } else {
      messageQueue.isEmpty
    }

    def remove(messageId: String): Unit = {
      if (isFifo) {
        messagesById.get(messageId).foreach { msg =>
            messagesbyMessageGroupId.get(msg.messageGroupId).foreach { prioQueue =>
              val newQueue = prioQueue.filterNot(_.id == messageId)
              if (newQueue.nonEmpty) {
                messagesbyMessageGroupId.put(msg.messageGroupId, newQueue)
              } else {
                messagesbyMessageGroupId.remove(msg.messageGroupId)
              }
            }
        }
      }
      messagesById.remove(messageId)
    }

    def filterNot(p: InternalMessage => Boolean): MessageQueue = {
      val newMessagesById = messagesById.filterNot { case (_, msg) => p(msg) }
      if (isFifo) {
        val newMessagesbyMessageGroupId = messagesbyMessageGroupId.flatMap { case (messageGroupId, messages) =>
          messages.filterNot(p) match {
            case newMessages if newMessages.nonEmpty => Some(messageGroupId -> newMessages)
            case _ => None
          }
        }
        new MessageQueue(isFifo, newMessagesById, mutable.PriorityQueue.empty, newMessagesbyMessageGroupId)
      } else {
        new MessageQueue(isFifo, newMessagesById, messageQueue.filterNot(p))
      }
    }

    def isFifoBoundByOtherMessage(internalMessage: InternalMessage, deliveryTime: Long, batch: List[MessageData]): Boolean = if (isFifo) {
      messagesbyMessageGroupId.contains(internalMessage.messageGroupId) && messagesbyMessageGroupId(internalMessage.messageGroupId).exists { msg =>
        val isOtherMessage = msg.id != internalMessage.id
        val isNotInBatch = !batch.exists(_.id.id == msg.id)
        val isBeingHandled = msg.deliveryReceipt.isDefined && !msg.deliverable(deliveryTime)
        isOtherMessage && isNotInBatch && isBeingHandled
      }
    } else {
      false
    }

    def dequeue(accBatch: List[MessageData] = List.empty): Option[InternalMessage] = {
      if (isFifo) {
        dequeueFromFifo(accBatch)
      } else if (messageQueue.nonEmpty) {
        Some(messageQueue.dequeue())
      } else {
        None
      }
    }

    private def dequeueFromFifo(accBatch: List[MessageData],
      triedMessageGroups: Set[Option[String]] = Set.empty): Option[InternalMessage] = {
      val messageGroupIdHint = accBatch.lastOption.map(_.messageGroupId).filterNot(triedMessageGroups.contains)
      messageGroupIdHint.orElse(randomMessageGroup(triedMessageGroups)) match {
        case Some(messageGroupId) => dequeueFromMessageGroup(messageGroupId, accBatch).orElse {
          dequeueFromFifo(accBatch, triedMessageGroups + messageGroupId)
        }
        case None => None
      }
    }

    private def dequeueFromMessageGroup(messageGroupId: Option[String], accBatch: List[MessageData]): Option[InternalMessage] = {
      messagesbyMessageGroupId.get(messageGroupId) match {
        case Some(priorityQueue) if priorityQueue.nonEmpty =>
          val msg = nextVisibleMessage(priorityQueue, accBatch)
          if (priorityQueue.isEmpty) {
            messagesbyMessageGroupId.remove(messageGroupId)
          } else {
            messagesbyMessageGroupId += messageGroupId -> priorityQueue
          }
          msg
        case _ => None
      }
    }

    @tailrec
    private def nextVisibleMessage(priorityQueue: mutable.PriorityQueue[InternalMessage],
      accBatch: List[MessageData], accMessage: Seq[InternalMessage] = Seq.empty): Option[InternalMessage] = {
      if (priorityQueue.nonEmpty) {
        val msg = priorityQueue.dequeue()
        if (msg.deliverable(nowProvider.nowMillis)) {
          priorityQueue ++= accMessage
          Some(msg)
        } else if (accBatch.exists(_.id.id == msg.id)) {
          // If the message is invisible, we can only continue is the message is part of the current batch as we don't
          // want to return any other message in this message group as long as it's not been handled
          nextVisibleMessage(priorityQueue, accBatch, accMessage :+ msg)
        } else {
          priorityQueue += msg
          priorityQueue ++= accMessage
          None
        }
      } else {
        priorityQueue ++= accMessage
        None
      }
    }

    private def randomMessageGroup(triedMessageGroups: Set[Option[String]]): Option[Option[String]] = {
      val remainingMessageGroupIds = messagesbyMessageGroupId.keySet -- triedMessageGroups
      remainingMessageGroupIds.headOption
    }
  }

  var queueData: QueueData = initialQueueData
  var messageQueue = new MessageQueue(queueData.isFifo)
  val deadLettersQueueActor: Option[ActorRef] = deadLettersActorRef

  case class InternalMessage(id: String,
    var deliveryReceipt: Option[String],
    var nextDelivery: Long,
    content: String,
    messageAttributes: Map[String, MessageAttribute],
    created: DateTime,
    var firstReceive: Received,
    var receiveCount: Int,
    messageGroupId: Option[String],
    messageDeduplicationId: Option[String])
    extends Comparable[InternalMessage] {

    // Priority queues have biggest elements first
    def compareTo(other: InternalMessage): Int = {
      if (queueData.isFifo) {
        -created.getMillis.compareTo(other.created.getMillis)
      } else {
        -nextDelivery.compareTo(other.nextDelivery)
      }
    }

    def toMessageData = MessageData(
      MessageId(id),
      deliveryReceipt.map(DeliveryReceipt(_)),
      content,
      messageAttributes,
      MillisNextDelivery(nextDelivery),
      created,
      MessageStatistics(firstReceive, receiveCount),
      messageGroupId,
      messageDeduplicationId)

    def toNewMessageData = NewMessageData(
      Some(MessageId(id)),
      content,
      messageAttributes,
      MillisNextDelivery(nextDelivery),
      messageGroupId,
      messageDeduplicationId)

    def deliverable(deliveryTime: Long): Boolean = nextDelivery <= deliveryTime
  }

  object InternalMessage {
    def from(messageData: MessageData) = InternalMessage(
      messageData.id.id,
      messageData.deliveryReceipt.map(_.receipt),
      messageData.nextDelivery.millis,
      messageData.content,
      messageData.messageAttributes,
      messageData.created,
      messageData.statistics.approximateFirstReceive,
      messageData.statistics.approximateReceiveCount,
      messageData.messageGroupId,
      messageData.messageDeduplicationId)

    def from(newMessageData: NewMessageData) = InternalMessage(
      newMessageData.id.getOrElse(generateId()).id,
      None,
      newMessageData.nextDelivery.toMillis(nowProvider.nowMillis, queueData.delay.getMillis).millis,
      newMessageData.content,
      newMessageData.messageAttributes,
      nowProvider.now,
      NeverReceived,
      0,
      newMessageData.messageGroupId,
      newMessageData.messageDeduplicationId)
  }

  private def generateId() = MessageId(UUID.randomUUID().toString)
}
