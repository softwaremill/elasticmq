package org.elasticmq.storage

import squeryl.{DBConfiguration, SquerylStorage}

trait SquerylStorageTest extends StorageTest {
  val squerylDBConfiguration = DBConfiguration.h2(this.getClass.getName)
  
  override abstract def setups = StorageTestSetup("Squeryl",
    () => new SquerylStorage(squerylDBConfiguration)) :: super.setups
}

class MessageCommandsSquerylStorageTest extends MessageCommandsTest with SquerylStorageTest
class MessageStatisticsCommandsSquerylStorageTest extends MessageStatisticsCommandsTest with SquerylStorageTest
class QueueCommandsSquerylStorageTest extends QueueCommandsTest with SquerylStorageTest
