package org.elasticmq.replication.jgroups

import org.elasticmq.storage._
import org.jgroups.JChannel
import java.util.concurrent.atomic.AtomicReference
import org.elasticmq.data.DataSource
import org.elasticmq.replication.{ClusterState, CommandResultReplicator, CommandApplier, ReplicatedStorage}
import org.elasticmq._

class JGroupsReplicatedStorage(masterAddressRef: AtomicReference[Option[NodeAddress]],
                               delegateStorage: StorageCommandExecutor,
                               channel: JChannel,
                               commandResultReplicator: CommandResultReplicator,
                               myAdress: NodeAddress,
                               val clusterState: ClusterState)
  extends ReplicatedStorage with CommandApplier {

  def execute[R](command: StorageCommand[R]) = {
    clusterState.assertNodeActive()

    if (isMaster) {
      val result = delegateStorage.execute(command)
      commandResultReplicator.replicateIfMutated(command, result)
      result
    } else {
      throw new NodeIsNotMasterException(masterAddress)
    }
  }

  def executeStateManagement[T](f: (DataSource) => T) = delegateStorage.executeStateManagement(f)

  def apply(command: IdempotentMutativeCommand[_]) {
    delegateStorage.executeToleratingAppliedCommands(command)
  }

  def address = myAdress

  def masterAddress = masterAddressRef.get()

  def isMaster = Some(address) == masterAddress

  def shutdown() {
    channel.close()
  }
}


