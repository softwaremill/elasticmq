package org.elasticmq.storage

trait StorageModule {
  def storageCommandExecutor: StorageCommandExecutor
}