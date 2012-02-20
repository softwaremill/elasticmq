package org.elasticmq.storage

trait StorageCommandExecutor {
  def execute[R](command: StorageCommand[R]): R
}
