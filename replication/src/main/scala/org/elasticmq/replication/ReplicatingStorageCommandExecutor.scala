package org.elasticmq.replication

import org.elasticmq.storage._

class ReplicatingStorageCommandExecutor(delegate: StorageCommandExecutor) extends StorageCommandExecutor {
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

  def resultingMutations[R](command: StorageCommand[R], result: R): List[IdempotentMutativeCommand[_]] = {
    /*
    We can only replicate idempotent mutative commands. That is because during state transfer, it is possible that
    a mutation was applied to the state, which is being transferred, but the command has not yet been replicated
    (in another thread).
    So a command may end being re-applied on the new node.
     */
    command match {
      case c: CreateQueueCommand => c :: Nil
      case c: UpdateQueueCommand => c :: Nil
      case c: DeleteQueueCommand => c :: Nil

      case c: SendMessageCommand => c :: Nil
      case c: UpdateNextDeliveryCommand => c :: Nil
      case c: DeleteMessageCommand => c :: Nil
      case c: UpdateMessageStatisticsCommand => c :: Nil

      case ReceiveMessageCommand(queueName, deliveryTime, newNextDelivery) => {
        result match {
          case Some(messageData) => UpdateNextDeliveryCommand(queueName, messageData.id, messageData.nextDelivery) :: Nil
          case None => Nil
        }
      }         
        
      case _ => Nil
    }
  }

  def replicate(list: List[IdempotentMutativeCommand[_]]) {

  }
}
