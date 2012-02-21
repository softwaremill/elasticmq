package org.elasticmq.storage.inmemory

import org.elasticmq.storage.StorageModule

trait InMemoryStorageModule extends StorageModule {
  val storageCommandExecutor = new InMemoryStorageCommandExecutor
}
