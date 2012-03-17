package org.elasticmq.replication

import org.elasticmq.storage._
import org.jgroups.JChannel
import java.util.concurrent.atomic.AtomicReference
import org.elasticmq.{NodeIsNotMasterException, NodeAddress}

class JGroupsReplicatedStorage(masterAddressRef: AtomicReference[Option[NodeAddress]],
                               delegateStorage: StorageCommandExecutor,
                               channel: JChannel,
                               commandResultReplicator: JGroupsCommandResultReplicator,
                               myAdress: NodeAddress)
  extends ReplicatedStorage with CommandApplier {
  
  def execute[R](command: StorageCommand[R]) = {
    if (isMaster) {
      val result = delegateStorage.execute(command)
      commandResultReplicator.replicateIfMutated(command, result)
      result
    } else {
      throw new NodeIsNotMasterException(masterAddress)
    }
  }

  def apply(command: IdempotentMutativeCommand[_]) {
    delegateStorage.execute(command)
  }

  def address = myAdress

  def masterAddress = masterAddressRef.get()
  
  def isMaster = Some(address) == masterAddress

  def stop() {
    channel.close()
  }
}


