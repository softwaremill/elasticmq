package org.elasticmq.impl.nativeclient

import org.elasticmq.impl.MessageData
import org.elasticmq.{MillisVisibilityTimeout, MessageDoesNotExistException, Message, MessageId}
import org.elasticmq.storage.{MessageStatisticsStorageModule, MessageStorageModule}


trait NativeMessageModule {
  this: MessageStorageModule with MessageStatisticsStorageModule with NativeHelpersModule =>

  class NativeMessage(queueName: String, messageId: MessageId) extends Message with WithLazyAtomicData[MessageData] {
    def this(queueName: String, messageData: MessageData) = {
      this(queueName, messageData.id)
      data = messageData
    }

    def initData = messageStorage(queueName).lookupMessage(messageId)
      .getOrElse(throw new MessageDoesNotExistException(queueName, messageId))

    // Operations

    def updateVisibilityTimeout(newVisibilityTimeout: MillisVisibilityTimeout) = {
      messageStorage(queueName).updateVisibilityTimeout(id, computeNextDelivery(newVisibilityTimeout.millis))
      fetchMessage()
    }

    def fetchStatistics() = messageStatisticsStorage(queueName).readMessageStatistics(id)

    def delete() {
      messageStorage(queueName).deleteMessage(id)
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