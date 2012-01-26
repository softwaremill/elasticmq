package org.elasticmq.storage.inmemory

import collection.JavaConversions
import org.elasticmq._
import storage.MessageStorageModule
import java.util.concurrent.{PriorityBlockingQueue, ConcurrentHashMap}
import org.elasticmq.impl.MessageData
import scala.annotation.tailrec

trait InMemoryMessageStorageModule extends MessageStorageModule {
  this: InMemoryStorageModelModule with InMemoryMessageStatisticsStorageModule with InMemoryMessageStorageRegistryModule =>

  class OneQueueInMemoryMessageStorage(queueName: String) extends MessageStorage {
    val messagesById = JavaConversions.asScalaConcurrentMap(new ConcurrentHashMap[MessageId, InMemoryMessage])
    val messageQueue = new PriorityBlockingQueue[InMemoryMessage]()

    def persistMessage(message: MessageData) {
      val inMemoryMessage = InMemoryMessage.from(message)

      // First putting in the map so that the message is not considered deleted if it's received immediately after
      // putting in the queue.
      messagesById.put(MessageId(inMemoryMessage.id), inMemoryMessage)
      messageQueue.add(inMemoryMessage)
    }

    def updateVisibilityTimeout(messageId: MessageId, newNextDelivery: MillisNextDelivery) {
      val inMemoryMessage = messagesById
        .get(messageId)
        .getOrElse(throw new MessageDoesNotExistException(queueName, messageId))

      // TODO: in fact we only support *increasing* the next delivery

      // Locking
      inMemoryMessage.nextDeliveryState.set(NextDeliveryIsBeingUpdated)
      // Updating
      inMemoryMessage.nextDelivery.set(newNextDelivery.millis)
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
              // Putting the message back and letting the thread that updates the next delviery finish
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

      messageStatisticsStorage(queueName).removeMessageStatistics(messageId)
    }

    def lookupMessage(messageId: MessageId) = {
      messagesById.get(messageId).map(_.toMessageData)
    }
  }

  def messageStorage(queueName: String) = storageRegistry.getStoreForQueue(queueName)
}