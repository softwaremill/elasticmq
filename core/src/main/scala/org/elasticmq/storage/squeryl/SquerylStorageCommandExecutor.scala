package org.elasticmq.storage.squeryl

import org.elasticmq.storage._

class SquerylStorageCommandExecutor extends StorageCommandExecutor {
  val modules =
    new SquerylInitializerModule
      with SquerylSchemaModule
      with SquerylQueueStorageModule
      with SquerylMessageStorageModule
      with SquerylMessageStatisticsStorageModule

  def execute[R](command: StorageCommand[R]) = command match {
    case CreateQueueCommand(queue) => modules.queueStorage.createQueue(queue)
    case UpdateQueueCommand(queue) => modules.queueStorage.updateQueue(queue)
    case DeleteQueueCommand(name) => modules.queueStorage.deleteQueue(name)
    case LookupQueueCommand(name) => modules.queueStorage.lookupQueue(name)
    case ListQueuesCommand() => modules.queueStorage.listQueues
    case GetQueueStatisticsCommand(name, deliveryTime) => modules.queueStorage.queueStatistics(name, deliveryTime)

    case SendMessageCommand(queueName, message) => modules.messageStorage(queueName).sendMessage(message)
    case UpdateVisibilityTimeoutCommand(queueName, messageId, newNextDelivery) =>
      modules.messageStorage(queueName).updateVisibilityTimeout(messageId, newNextDelivery)
    case ReceiveMessageCommand(queueName, deliveryTime, newNextDelivery) =>
      modules.messageStorage(queueName).receiveMessage(deliveryTime, newNextDelivery)
    case DeleteMessageCommand(queueName, messageId) => modules.messageStorage(queueName).deleteMessage(messageId)
    case LookupMessageCommand(queueName, messageId) => modules.messageStorage(queueName).lookupMessage(messageId)

    case UpdateMessageStatisticsCommand(queueName, messageId, messageStatistics) =>
      modules.messageStatisticsStorage(queueName).updateMessageStatistics(messageId, messageStatistics)
    case GetMessageStatisticsCommand(queueName, messageId) =>
      modules.messageStatisticsStorage(queueName).readMessageStatistics(messageId)
  }
}
