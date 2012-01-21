package org.elasticmq.storage.inmemory

import collection.JavaConversions
import org.elasticmq._
import storage.MessageStorageModule
import java.util.concurrent.{PriorityBlockingQueue, ConcurrentHashMap}
import java.lang.IllegalStateException

trait InMemoryMessageStorageModule extends MessageStorageModule {
  this: InMemoryStorageModel with InMemoryMessageStatisticsStorageModule =>

  class InMemoryMessageStorage extends MessageStorage {
    private val messageStores = JavaConversions.asScalaConcurrentMap(
      new ConcurrentHashMap[String, OneQueueInMemoryMessageStorage])

    def persistMessage(message: SpecifiedMessage) { getStoreForQueue(message.queue.name).persistMessage(message) }

    def updateVisibilityTimeout(message: SpecifiedMessage, newNextDelivery: MillisNextDelivery) =
      getStoreForQueue(message.queue.name).updateVisibilityTimeout(message, newNextDelivery)

    def receiveMessage(queue: Queue, deliveryTime: Long, newNextDelivery: MillisNextDelivery) =
      getStoreForQueue(queue.name).receiveMessage(queue, deliveryTime, newNextDelivery)

    def deleteMessage(message: IdentifiableMessage) {
      getStoreForQueue(message.queue.name).deleteMessage(message)
    }

    def lookupMessage(queue: Queue, id: String) = getStoreForQueue(queue.name).lookupMessage(queue, id)

    def createStorForQueue(queue: String) {
      messageStores.put(queue, new OneQueueInMemoryMessageStorage())
    }

    def deleteStoreForQueue(queue: String) {
      messageStores.remove(queue)
    }

    def getStoreForQueue(queue: String): OneQueueInMemoryMessageStorage = {
      messageStores.get(queue).getOrElse(throw new IllegalStateException("Unknown queue: "+queue))
    }
  }
  
  class OneQueueInMemoryMessageStorage() extends MessageStorage {
    val messagesById = JavaConversions.asScalaConcurrentMap(new ConcurrentHashMap[String, InMemoryMessage])
    val messageQueue = new PriorityBlockingQueue[InMemoryMessage]()

    def persistMessage(message: SpecifiedMessage) {
      val inMemoryMessage = InMemoryMessage.from(message)

      // First putting in the map so that the message is not considered deleted if it's received immediately after
      // putting in the queue.
      messagesById.put(inMemoryMessage.id, inMemoryMessage)
      messageQueue.add(inMemoryMessage)
    }

    def updateVisibilityTimeout(message: SpecifiedMessage, newNextDelivery: MillisNextDelivery) = {
      val inMemoryMessage = messagesById
        .get(message.id.get)
        .getOrElse(throw new IllegalStateException("Unknown message: "+message))

      // TODO: in fact we only support *increasing* the next delivery

      // Locking
      inMemoryMessage.nextDeliveryState.set(NextDeliveryIsBeingUpdated)
      // Updating
      inMemoryMessage.nextDelivery.set(newNextDelivery.millis)
      // Releasing lock
      inMemoryMessage.nextDeliveryState.set(NextDeliveryUpdated)

      inMemoryMessage.toMessage(message.queue)
    }

    // TODO: tailrec
    def receiveMessage(queue: Queue, deliveryTime: Long, newNextDelivery: MillisNextDelivery): Option[SpecifiedMessage] = {
      messageQueue.poll() match {
        case null => None
        case message => {
          message.nextDeliveryState.get() match {
            case NextDeliveryIsBeingUpdated => {
              // Putting the message back and letting the thread that updates the next delviery finish
              messageQueue.add(message)
              receiveMessage(queue, deliveryTime, newNextDelivery)
            }
            case NextDeliveryUpdated => {
              message.nextDeliveryState.set(NextDeliveryUnchanged)
              messageQueue.add(message)
              receiveMessage(queue, deliveryTime, newNextDelivery)
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

                Some(message.toMessage(queue))
              } else {
                // Deleted message - trying again
                receiveMessage(queue, deliveryTime, newNextDelivery)
              }
            }
          }
        }
      }
    }

    def deleteMessage(message: IdentifiableMessage) {
      // Just removing the message from the map. The message will be removed from the queue when trying to receive it.
      messagesById.remove(message.id.get)

      messageStatisticsStorage.removeMessageStatistics(message)
    }

    def lookupMessage(queue: Queue, id: String) = {
      messagesById.get(id).map(_.toMessage(queue))
    }
  }

  val messageStorage = new InMemoryMessageStorage
}