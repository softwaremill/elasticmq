package org.elasticmq.storage

import org.elasticmq.data.{MessageData, QueueData}
import org.elasticmq.{MessageStatistics, MillisNextDelivery, MessageId, QueueStatistics}

sealed trait StorageCommand[R] {
  def resultingMutations(result: R): List[IdempotentMutativeCommand[_]]
}

trait MutativeCommand[R] extends StorageCommand[R]
trait IdempotentMutativeCommand[R] extends MutativeCommand[R] {
  def resultingMutations(result: R) = this :: Nil
}
trait NonMutativeCommand[R] extends StorageCommand[R] {
  def resultingMutations(result: R) = Nil
}

case class CreateQueueCommand(queue: QueueData) extends StorageCommand[Unit] with IdempotentMutativeCommand[Unit]
case class UpdateQueueCommand(queue: QueueData) extends StorageCommand[Unit] with IdempotentMutativeCommand[Unit]
case class DeleteQueueCommand(name: String) extends StorageCommand[Unit] with IdempotentMutativeCommand[Unit]
case class LookupQueueCommand(name: String) extends StorageCommand[Option[QueueData]] with NonMutativeCommand[Option[QueueData]]
case class ListQueuesCommand() extends StorageCommand[Seq[QueueData]] with NonMutativeCommand[Seq[QueueData]]
case class GetQueueStatisticsCommand(name: String, deliveryTime: Long) extends StorageCommand[QueueStatistics] with NonMutativeCommand[QueueStatistics]

case class SendMessageCommand(queueName: String, message: MessageData)
  extends StorageCommand[Unit] with IdempotentMutativeCommand[Unit]

case class UpdateNextDeliveryCommand(queueName: String, messageId: MessageId, newNextDelivery: MillisNextDelivery)
  extends StorageCommand[Unit] with IdempotentMutativeCommand[Unit]

case class ReceiveMessagesCommand(queueName: String, deliveryTime: Long, newNextDelivery: MillisNextDelivery, maxCount: Int)
  extends StorageCommand[List[MessageData]] with MutativeCommand[List[MessageData]] {

  def resultingMutations(result: List[MessageData]) = {
    result.map(messageData => UpdateNextDeliveryCommand(queueName, messageData.id, messageData.nextDelivery))
  }
}

case class DeleteMessageCommand(queueName: String, messageId: MessageId)
  extends StorageCommand[Unit] with IdempotentMutativeCommand[Unit]

case class LookupMessageCommand(queueName: String, messageId: MessageId) extends StorageCommand[Option[MessageData]] with NonMutativeCommand[Option[MessageData]]

case class UpdateMessageStatisticsCommand(queueName: String, messageId: MessageId, messageStatistics: MessageStatistics)
  extends StorageCommand[Unit] with IdempotentMutativeCommand[Unit]

case class GetMessageStatisticsCommand(queueName: String, messageId: MessageId)
  extends StorageCommand[MessageStatistics] with NonMutativeCommand[MessageStatistics]

case class ClearStorageCommand() extends StorageCommand[Unit] with IdempotentMutativeCommand[Unit]