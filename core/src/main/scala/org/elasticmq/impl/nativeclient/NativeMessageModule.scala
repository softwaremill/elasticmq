package org.elasticmq.impl.nativeclient

import org.elasticmq.data.MessageData
import org.elasticmq.{MillisVisibilityTimeout, MessageDoesNotExistException, Message, MessageId}
import com.weiglewilczek.slf4s.Logging
import org.elasticmq.storage._

trait NativeMessageModule {
  this: StorageModule with NativeHelpersModule =>

  class NativeMessage(queueName: String, messageId: MessageId) extends Message 
    with WithLazyAtomicData[MessageData] with Logging {
    def this(queueName: String, messageData: MessageData) = {
      this(queueName, messageData.id)
      data = messageData
    }

    def initData = storageCommandExecutor.execute(new LookupMessageCommand(queueName, messageId))
      .getOrElse(throw new MessageDoesNotExistException(queueName, messageId))

    // Operations

    def updateVisibilityTimeout(newVisibilityTimeout: MillisVisibilityTimeout) = {
      storageCommandExecutor.execute(
        UpdateVisibilityTimeoutCommand(queueName, id, computeNextDelivery(newVisibilityTimeout.millis)))
      
      logger.debug("Updated visibility timeout of message: %s in queue: %s to: %s"
        .format(messageId, queueName, newVisibilityTimeout))
      fetchMessage()
    }

    def fetchStatistics() = storageCommandExecutor.execute(GetMessageStatisticsCommand(queueName, id))

    def delete() {
      storageCommandExecutor.execute(DeleteMessageCommand(queueName, id))
      logger.debug("Deleted message: %s".format(id))
    }

    def fetchMessage() = {
      clearData()
      this
    }

    // Message

    def content = data.content

    def id = data.id

    def nextDelivery = data.nextDelivery

    def created = data.created
  }
}