package org.elasticmq.storage.squeryl

trait SquerylStorageConfigurationModule {
  trait SquerylStorageConfiguration {
    def maxPendingMessageCandidates: Int
  }

  val configuration = new SquerylStorageConfiguration() {
    val maxPendingMessageCandidates = Runtime.getRuntime.availableProcessors() * 3
  }
}
