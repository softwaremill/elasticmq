package org.elasticmq.storage.inmemory

import org.elasticmq.storage._

class InMemoryStorageCommandExecutor extends StorageCommandExecutor {
  val queues = new InMemoryQueuesStorage(queueData => {
    val statistics = new InMemoryMessageStatisticsStorage(queueData.name)
    new InMemoryQueue(
      queueData,
      new InMemoryMessageStorage(queueData.name, statistics),
      statistics)
  })

  def execute[R](command: StorageCommand[R]) = command match {
    case CreateQueueCommand(queue) => queues.createQueue(queue)
    case UpdateQueueCommand(queue) => queues.updateQueue(queue)
    case DeleteQueueCommand(name) => queues.deleteQueue(name)
    case LookupQueueCommand(name) => queues.lookupQueue(name)
    case ListQueuesCommand() => queues.listQueues
    case GetQueueStatisticsCommand(name, deliveryTime) => queues.queueStatistics(name, deliveryTime)

    case SendMessageCommand(queueName, message) => queues(queueName).messages.sendMessage(message)
    case UpdateVisibilityTimeoutCommand(queueName, messageId, newNextDelivery) => 
      queues(queueName).messages.updateVisibilityTimeout(messageId, newNextDelivery)
    case ReceiveMessageCommand(queueName, deliveryTime, newNextDelivery) => 
      queues(queueName).messages.receiveMessage(deliveryTime, newNextDelivery)
    case DeleteMessageCommand(queueName, messageId) => queues(queueName).messages.deleteMessage(messageId)
    case LookupMessageCommand(queueName, messageId) => queues(queueName).messages.lookupMessage(messageId)

    case UpdateMessageStatisticsCommand(queueName, messageId, messageStatistics) => 
      queues(queueName).statistics.updateMessageStatistics(messageId, messageStatistics)
    case GetMessageStatisticsCommand(queueName, messageId) =>
      queues(queueName).statistics.readMessageStatistics(messageId)
  }
}
