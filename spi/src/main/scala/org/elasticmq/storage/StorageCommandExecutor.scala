package org.elasticmq.storage

import org.elasticmq.data.DataSource

trait StorageCommandExecutor {
  def execute[R](command: StorageCommand[R]): R
  def executeStateManagement[T](f: DataSource => T): T
  def shutdown()
}
