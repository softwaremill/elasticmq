package org.elasticmq.storage

import org.elasticmq.data.{MessageData, QueueData}
import org.elasticmq.{MessageStatistics, MillisNextDelivery, MessageId, QueueStatistics}

sealed trait StorageCommand[R]

case class CreateQueueCommand(queue: QueueData) extends StorageCommand[Unit]
case class UpdateQueueCommand(queue: QueueData) extends StorageCommand[Unit]
case class DeleteQueueCommand(name: String) extends StorageCommand[Unit]
case class LookupQueueCommand(name: String) extends StorageCommand[Option[QueueData]]
case class ListQueuesCommand() extends StorageCommand[Seq[QueueData]]
case class GetQueueStatisticsCommand(name: String, deliveryTime: Long) extends StorageCommand[QueueStatistics]

case class PersistMessageCommand(message: MessageData) extends StorageCommand[Unit]
case class UpdateVisibilityTimeoutCommand(messageId: MessageId, newNextDelivery: MillisNextDelivery) extends StorageCommand[Unit]
case class ReceiveMessageCommand(deliveryTime: Long, newNextDelivery: MillisNextDelivery) extends StorageCommand[Option[MessageData]]
case class DeleteMessageCommand(messageId: MessageId) extends StorageCommand[Unit]
case class LookupMessageCommand(messageId: MessageId) extends StorageCommand[Option[MessageData]]

case class UpdateMessageStatisticsCommand(messageId: MessageId, messageStatistics: MessageStatistics) extends StorageCommand[Unit]
case class GetMessageStatisticsCommand(messageId: MessageId) extends StorageCommand[MessageStatistics]
