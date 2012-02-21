package org.elasticmq.storage.squeryl

import org.elasticmq.storage.StorageModule

trait SquerylStorageModule extends StorageModule {
  def storageCommandExecutor = new SquerylStorageCommandExecutor
}