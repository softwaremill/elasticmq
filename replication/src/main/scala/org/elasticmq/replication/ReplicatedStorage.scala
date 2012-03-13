package org.elasticmq.replication

import org.elasticmq.storage.StorageCommandExecutor

trait ReplicatedStorage extends StorageCommandExecutor {
  def isMaster: Boolean
  def stop()
}
