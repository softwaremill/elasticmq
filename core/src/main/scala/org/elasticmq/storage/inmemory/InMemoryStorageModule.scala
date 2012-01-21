package org.elasticmq.storage.inmemory

import org.elasticmq.storage.StorageModule

trait InMemoryStorageModule extends StorageModule with
  InMemoryMessageStatisticsStorageModule with
  InMemoryMessageStorageModule with
  InMemoryQueueStorageModule with
  InMemoryStorageModelModule