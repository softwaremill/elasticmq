package org.elasticmq.replication

import org.elasticmq.storage.StorageCommandExecutor
import org.elasticmq.NodeAddress

trait ReplicatedStorage extends StorageCommandExecutor {
  def isMaster: Boolean
  def address: NodeAddress
  def masterAddress: Option[NodeAddress]
  def stop()
}
