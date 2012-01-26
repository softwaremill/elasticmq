package org.elasticmq.storage.inmemory

import collection.JavaConversions
import org.elasticmq._
import storage.MessageStorageModule
import java.util.concurrent.{PriorityBlockingQueue, ConcurrentHashMap}
import org.elasticmq.impl.MessageData

trait InMemoryMessageStorageModule extends MessageStorageModule {
  this: InMemoryStorageModelModule with InMemoryMessageStatisticsStorageModule with InMemoryMessageStorageRegistryModule =>

  class OneQueueInMemoryMessageStorage(queueName: String) extends MessageStorage {
    val messagesById = JavaConversions.asScalaConcurrentMap(new ConcurrentHashMap[String, InMemoryMessage])
    val messageQueue = new PriorityBlockingQueue[InMemoryMessage]()

    def persistMessage(message: MessageData) {
      val inMemoryMessage = InMemoryMessage.from(message)

      // First putting in the map so that the message is not considered deleted if it's received immediately after
      // putting in the queue.
      messagesById.put(inMemoryMessage.id, inMemoryMessage)
      messageQueue.add(inMemoryMessage)
    }

    def updateVisibilityTimeout(messageId: MessageId, newNextDelivery: MillisNextDelivery) {
      val inMemoryMessage = messagesById
        .get(messageId.id)
        .getOrElse(throw new MessageDoesNotExistException(messageId, queueName))

      // TODO: in fact we only support *increasing* the next delivery

      // Locking
      inMemoryMessage.nextDeliveryState.set(NextDeliveryIsBeingUpdated)
      // Updating
      inMemoryMessage.nextDelivery.set(newNextDelivery.millis)
      // Releasing lock
      inMemoryMessage.nextDeliveryState.set(NextDeliveryUpdated)
    }

    // TODO: tailrec
    def receiveMessage(deliveryTime: Long, newNextDelivery: MillisNextDelivery): Option[MessageData] = {
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
              } else if (messagesById.contains(message.id)) {
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
      messagesById.remove(messageId.id)

      messageStatisticsStorage(queueName).removeMessageStatistics(messageId)
    }

    def lookupMessage(messageId: MessageId) = {
      messagesById.get(messageId.id).map(_.toMessageData)
    }
  }

  def messageStorage(queueName: String) = storageRegistry.getStoreForQueue(queueName)
}