package org.elasticmq.replication

import org.elasticmq.storage._


class ReplicationStorageCommandExecutor(delegate: StorageCommandExecutor) extends StorageCommandExecutor {
  def execute[R](command: StorageCommand[R]) = {
    val result = delegate.execute(command)
    replicateIfMutated(command, result)
    result
  }

  def replicateIfMutated[R](command: StorageCommand[R], result: R) {
    val mutations = resultingMutations(command, result)
    if (mutations.size > 0) {
      replicate(mutations)
    }
  }

  def resultingMutations[R](command: StorageCommand[R], result: R): List[MutativeCommand[_]] = {
    command match {
      case c: CreateQueueCommand => c :: Nil
      case c: UpdateQueueCommand => c :: Nil
      case c: DeleteQueueCommand => c :: Nil

      case c: SendMessageCommand => c :: Nil
      case c: UpdateVisibilityTimeoutCommand => c :: Nil
      case c: DeleteMessageCommand => c :: Nil
      case c: UpdateMessageStatisticsCommand => c :: Nil

      case ReceiveMessageCommand(queueName, deliveryTime, newNextDelivery) => {
        result match {
          case Some(messageData) => UpdateVisibilityTimeoutCommand(queueName, messageData.id, messageData.nextDelivery) :: Nil
          case None => Nil
        }
      }         
        
      case _ => Nil
    }
  }

  def replicate(list: List[MutativeCommand[_]]) {

  }
}
