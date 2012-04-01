package org.elasticmq.replication.jgroups

import org.elasticmq.storage._
import org.jgroups.JChannel
import java.util.concurrent.atomic.AtomicReference
import org.elasticmq.data.DataSource
import org.elasticmq.replication.{ClusterState, CommandResultReplicator, CommandApplier, ReplicatedStorage}
import org.elasticmq._
import com.weiglewilczek.slf4s.Logging

class JGroupsReplicatedStorage(masterAddressRef: AtomicReference[Option[NodeAddress]],
                               delegateStorage: StorageCommandExecutor,
                               channel: JChannel,
                               commandResultReplicator: CommandResultReplicator,
                               myAdress: NodeAddress,
                               val clusterState: ClusterState)
  extends ReplicatedStorage with CommandApplier with Logging {

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
    import scala.util.control.Exception._

    handling(classOf[QueueDoesNotExistException],
      classOf[QueueAlreadyExistsException],
      classOf[MessageDoesNotExistException])
    .by(logger.warn("Exception when applying command; a conflicting command was already applied to the storage. " +
      "This can happen immediately after state replication, if commands were executed on the master when the " +
      "state was being read.", _))
    .apply {
      delegateStorage.execute(command)
    }
  }

  def address = myAdress

  def masterAddress = masterAddressRef.get()

  def isMaster = Some(address) == masterAddress

  def shutdown() {
    channel.close()
  }
}


