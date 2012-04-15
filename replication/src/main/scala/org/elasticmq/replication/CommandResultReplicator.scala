package org.elasticmq.replication

import org.elasticmq.storage._
import org.elasticmq.replication.message.ApplyCommands

class CommandResultReplicator(delegateStorage: StorageCommandExecutor,
                              replicationMessageSender: ReplicationMessageSender) {

  def replicateIfMutated[R](command: StorageCommand[R], result: R) {
    /*
     We can only replicate idempotent mutative commands. That is because during state transfer, it is possible that
     a mutation was applied to the state, which is being transferred, but the command has not yet been replicated
     (in another thread).
     So a command may end being re-applied on the new node.
    */
    val mutations = command.resultingMutations(result)
    if (mutations.size > 0) {
      replicate(mutations)
    }
  }

  private def replicate(list: List[IdempotentMutativeCommand[_]]) {
    replicationMessageSender.broadcast(ApplyCommands(list))
  }
}
