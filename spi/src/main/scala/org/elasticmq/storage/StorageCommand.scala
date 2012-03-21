package org.elasticmq.storage

import org.elasticmq.data.{MessageData, QueueData}
import org.elasticmq.{MessageStatistics, MillisNextDelivery, MessageId, QueueStatistics}
import java.io.{InputStream, OutputStream}

sealed trait StorageCommand[R]

trait MutativeCommand[R] extends StorageCommand[R]
trait IdempotentMutativeCommand[R] extends MutativeCommand[R]

case class CreateQueueCommand(queue: QueueData) extends StorageCommand[Unit] with IdempotentMutativeCommand[Unit]
case class UpdateQueueCommand(queue: QueueData) extends StorageCommand[Unit] with IdempotentMutativeCommand[Unit]
case class DeleteQueueCommand(name: String) extends StorageCommand[Unit] with IdempotentMutativeCommand[Unit]
case class LookupQueueCommand(name: String) extends StorageCommand[Option[QueueData]] 
case class ListQueuesCommand() extends StorageCommand[Seq[QueueData]]
case class GetQueueStatisticsCommand(name: String, deliveryTime: Long) extends StorageCommand[QueueStatistics]


case class SendMessageCommand(queueName: String, message: MessageData)
  extends StorageCommand[Unit] with IdempotentMutativeCommand[Unit]

case class UpdateNextDeliveryCommand(queueName: String, messageId: MessageId, newNextDelivery: MillisNextDelivery)
  extends StorageCommand[Unit] with IdempotentMutativeCommand[Unit]

case class ReceiveMessageCommand(queueName: String, deliveryTime: Long, newNextDelivery: MillisNextDelivery)
  extends StorageCommand[Option[MessageData]] with MutativeCommand[Option[MessageData]]

case class DeleteMessageCommand(queueName: String, messageId: MessageId)
  extends StorageCommand[Unit] with IdempotentMutativeCommand[Unit]

case class LookupMessageCommand(queueName: String, messageId: MessageId) extends StorageCommand[Option[MessageData]]


case class UpdateMessageStatisticsCommand(queueName: String, messageId: MessageId, messageStatistics: MessageStatistics)
  extends StorageCommand[Unit] with IdempotentMutativeCommand[Unit]

case class GetMessageStatisticsCommand(queueName: String, messageId: MessageId)
  extends StorageCommand[MessageStatistics]

case class ClearStorageCommand() extends StorageCommand[Unit] with IdempotentMutativeCommand[Unit]