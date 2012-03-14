package org.elasticmq.replication

import org.elasticmq.storage._
import org.jgroups.JChannel
import java.util.concurrent.atomic.AtomicBoolean

class JGroupsReplicatedStorage(nodeIsMaster: AtomicBoolean,
                               delegateStorage: StorageCommandExecutor,
                               channel: JChannel,
                               commandResultReplicator: JGroupsCommandResultReplicator) extends ReplicatedStorage with CommandApplier {
  def execute[R](command: StorageCommand[R]) = {
    if (isMaster) {
      val result = delegateStorage.execute(command)
      commandResultReplicator.replicateIfMutated(command, result)
      result
    } else {
      throw new RuntimeException("Commands can only be executed on the master.")
    }
  }

  def apply(command: IdempotentMutativeCommand[_]) = {
    delegateStorage.execute(command)
  }


  def isMaster = nodeIsMaster.get

  def stop() {
    channel.close()
  }
}


