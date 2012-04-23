package org.elasticmq.storage.filelog

import org.scalatest.matchers.MustMatchers
import org.elasticmq.storage.inmemory.InMemoryStorage
import org.elasticmq.test._
import org.scalatest.{BeforeAndAfter, FunSuite}
import org.elasticmq.storage.{LookupMessageCommand, SendMessageCommand, CreateQueueCommand}
import org.elasticmq.{MessageId, MillisNextDelivery, MillisVisibilityTimeout}

class FileLogStorageTest extends FunSuite with MustMatchers with DataCreationHelpers with BeforeAndAfter {
  var configuration: FileLogConfiguration = _
  var fileLogDataDir: FileLogDataDir = _

  before {
    configuration = FileLogConfiguration(createTempDir(), 5)
    fileLogDataDir = new FileLogDataDir(configuration)
  }

  after {
    deleteDirRecursively(configuration.storageDir)
  }

  test("should log and restore without log rotation") {
    // Given
    val fileLogStorage = newFileLogStorage()

    // When
    fileLogStorage.execute(new CreateQueueCommand(createQueueData("q1", MillisVisibilityTimeout(10000L))))
    fileLogStorage.execute(new SendMessageCommand("q1", createMessageData("m1", "c1", MillisNextDelivery(0L))))
    fileLogStorage.execute(new SendMessageCommand("q1", createMessageData("m2", "c2", MillisNextDelivery(0L))))
    fileLogStorage.execute(new SendMessageCommand("q1", createMessageData("m3", "c3", MillisNextDelivery(0L))))

    fileLogStorage.shutdown()

    val fileLogStorage2 = newFileLogStorage()

    // Then
    fileLogStorage2.execute(new LookupMessageCommand("q1", MessageId("m2"))).map(_.content) must be (Some("c2"))
  }

  test("should log and restore with log rotation") {
    // Given
    val fileLogStorage = newFileLogStorage()

    // When
    fileLogStorage.execute(new CreateQueueCommand(createQueueData("q1", MillisVisibilityTimeout(10000L))))
    for (i <- 1 to 23) {
      fileLogStorage.execute(new SendMessageCommand("q1", createMessageData("m" + i, "c" + i, MillisNextDelivery(0L))))
    }

    fileLogStorage.shutdown()

    val fileLogStorage2 = newFileLogStorage()

    // Then
    for (i <- 1 to 23) {
      fileLogStorage2.execute(new LookupMessageCommand("q1", MessageId("m" + i))).map(_.content) must be (Some("c" + i))
    }
  }

  private def newFileLogStorage() = {
    new FileLogConfigurator(new InMemoryStorage, configuration).start()
  }
}
