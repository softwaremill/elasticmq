package org.elasticmq.replication

import org.elasticmq.storage.{StorageCommand, IdempotentMutativeCommand, StorageCommandExecutor}

class SlaveStorageCommandExecutor(delegate: StorageCommandExecutor) extends StorageCommandExecutor {
  def execute[R](command: StorageCommand[R]) = {
    throw new RuntimeException("Commands can only be executed on the master.")
  }

  def applyReplicatedCommand[R](command: IdempotentMutativeCommand[R]) {
    delegate.execute(command)
  }
}
