package org.elasticmq.storage.inmemory

import org.elasticmq.data.MessageData
import org.elasticmq._

import java.util.concurrent.{ConcurrentHashMap, PriorityBlockingQueue}
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}

import scala.annotation.tailrec
import scala.collection.JavaConversions

import org.joda.time.DateTime
import org.elasticmq.storage.interfaced.MessagesStorage

class InMemoryMessagesStorage(queueName: String, statistics: InMemoryMessageStatisticsStorage) extends MessagesStorage {
  val messagesById = JavaConversions.asScalaConcurrentMap(new ConcurrentHashMap[MessageId, InMemoryMessage])
  val messageQueue = new PriorityBlockingQueue[InMemoryMessage]()

  def sendMessage(message: MessageData) {
    val inMemoryMessage = InMemoryMessage.from(message)

    // First writing empty stats. This must be done before putting the message in the queue, as if we did it after,
    // the message may get received and the stats updated.
    statistics.updateMessageStatistics(message.id, MessageStatistics(NeverReceived, 0))

    // First putting in the map so that the message is not considered deleted if it's received immediately after
    // putting in the queue.
    messagesById.put(MessageId(inMemoryMessage.id), inMemoryMessage)
    messageQueue.add(inMemoryMessage)
  }

  def updateNextDelivery(messageId: MessageId, newNextDelivery: MillisNextDelivery) {
    val inMemoryMessage = messagesById
      .get(messageId)
      .getOrElse(throw new MessageDoesNotExistException(queueName, messageId))

    // Locking
    inMemoryMessage.nextDeliveryState.set(NextDeliveryIsBeingUpdated)

    // Updating
    val oldNextDelivery = inMemoryMessage.nextDelivery.getAndSet(newNextDelivery.millis)
    if (newNextDelivery.millis < oldNextDelivery) {
      // We have to re-insert the message, as another message with a bigger next delivery may be now before it,
      // so the message wouldn't be correctly received.
      // (!) This may be slow (!)
      // Only inserting if removal was successfull (the message may have been removed from the queue concurrently).
      if (messageQueue.remove(inMemoryMessage)) {
        messageQueue.add(inMemoryMessage)
      }
    }
    // Else:
    // Just increasing the next delivery. Common case. It is enough to increase the value in the object. No need to
    // re-insert the message into the queue, as it will be reinserted if needed during receiving.

    // Releasing lock
    inMemoryMessage.nextDeliveryState.set(NextDeliveryUpdated)
  }

  @tailrec
  final def receiveMessage(deliveryTime: Long, newNextDelivery: MillisNextDelivery): Option[MessageData] = {
    messageQueue.poll() match {
      case null => None
      case message => {
        message.nextDeliveryState.get() match {
          case NextDeliveryIsBeingUpdated => {
            // Putting the message back and letting the thread that updates the next delivery finish
            messageQueue.add(message)
            receiveMessage(deliveryTime, newNextDelivery)
          }
          case NextDeliveryUpdated => {
            message.nextDeliveryState.set(NextDeliveryUnchanged)
            messageQueue.add(message)
            receiveMessage(deliveryTime, newNextDelivery)
          }
          case NextDeliveryUnchanged => {
            if (message.nextDelivery.get() > deliveryTime) {
              // Putting the message back. That's the youngest message, so there is no message that can be received.
              messageQueue.add(message)
              None
            } else if (messagesById.contains(MessageId(message.id))) {
              // Putting the message again into the queue, with a new next delivery
              message.nextDelivery.set(newNextDelivery.millis)
              messageQueue.add(message)

              Some(message.toMessageData)
            } else {
              // Deleted message - trying again
              receiveMessage(deliveryTime, newNextDelivery)
            }
          }
        }
      }
    }
  }

  def deleteMessage(messageId: MessageId) {
    // Just removing the message from the map. The message will be removed from the queue when trying to receive it.
    messagesById.remove(messageId)

    statistics.deleteMessageStatistics(messageId)
  }

  def lookupMessage(messageId: MessageId) = {
    messagesById.get(messageId).map(_.toMessageData)
  }
}

case class InMemoryMessage(id: String,
                           deliveryReceipt: Option[String],
                           nextDelivery: AtomicLong,
                           content: String,
                           created: DateTime,
                           nextDeliveryState: AtomicReference[MessageNextDeliveryState])
  extends Comparable[InMemoryMessage] {

  def compareTo(other: InMemoryMessage) = nextDelivery.get().compareTo(other.nextDelivery.get())

  def toMessageData = MessageData(MessageId(id), deliveryReceipt.map(DeliveryReceipt(_)), content,
    MillisNextDelivery(nextDelivery.get()), created)
}

object InMemoryMessage {
  def from(message: MessageData) = InMemoryMessage(
    message.id.id,
    message.deliveryReceipt.map(_.receipt),
    new AtomicLong(message.nextDelivery.millis),
    message.content,
    message.created,
    new AtomicReference(NextDeliveryUnchanged))
}

sealed abstract class MessageNextDeliveryState

case object NextDeliveryUnchanged extends MessageNextDeliveryState

// The message's next delivery is being updated. The message should be re-inserted into the queue. This may cause
// multiple tries to receive the message and put it back (while the next delivery is updated). So in fact this is
// an active lock.
case object NextDeliveryIsBeingUpdated extends MessageNextDeliveryState

// The message's next delivery has been updated. When received, it must be re-inserted to the right position
// in the queue.
case object NextDeliveryUpdated extends MessageNextDeliveryState