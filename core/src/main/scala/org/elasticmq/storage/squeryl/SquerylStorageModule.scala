package org.elasticmq.storage.squeryl

import org.elasticmq.storage.StorageModule

trait SquerylStorageModule extends StorageModule with
  SquerylInitializerModule with
  SquerylMessageStatisticsStorageModule with
  SquerylMessageStorageModule with
  SquerylQueueStorageModule with
  SquerylSchemaModule