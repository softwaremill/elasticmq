package org.elasticmq.storage

import inmemory.InMemoryStorage

trait InMemoryStorageTest extends StorageTest {
  override abstract def setups = StorageTestSetup("In memory", () => new InMemoryStorage()) :: super.setups
}

class MessageCommandsInMemoryStorageTest extends MessageCommandsTest with InMemoryStorageTest
class MessageStatisticsCommandsInMemoryStorageTest extends MessageStatisticsCommandsTest with InMemoryStorageTest
class QueueCommandsInMemoryStorageTest extends QueueCommandsTest with InMemoryStorageTest
