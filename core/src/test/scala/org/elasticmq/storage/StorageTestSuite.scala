package org.elasticmq.storage

import org.scalatest.matchers.MustMatchers
import org.scalatest._
import org.elasticmq.{Message, Queue}
import squeryl.SquerylStorage

trait StorageTestSuite extends FunSuite with MustMatchers with OneInstancePerTest {
  private case class StorageTestSetup(storageName: String, storage: Storage, initialize: () => Unit, shutdown: () => Unit)

  private val setups: List[StorageTestSetup] =
    StorageTestSetup("Squeryl", new SquerylStorage, () => SquerylStorage.initialize(this.getClass.getName), SquerylStorage.shutdown _) :: Nil

  private var _storage: Storage = null

  abstract override protected def test(testName: String, testTags: Tag*)(testFun: => Unit) {
    for (setup <- setups) {
      super.test(testName+" using "+setup.storageName, testTags: _*) {
        _storage = setup.storage
        setup.initialize()
        testFun
        setup.shutdown()
      }
    }
  }

  def storage: Storage = _storage
}

class QueueStorageTestSuite extends StorageTestSuite {
  test("non-existent queue should not be found") {
    // Given
    storage.queueStorage.persistQueue(Queue("q1", 10L))

    // When
    val lookupResult = storage.queueStorage.lookupQueue("q2")

    // Then
    lookupResult must be (None)
  }

  test("after persisting a queue it should be found") {
    // Given
    storage.queueStorage.persistQueue(Queue("q1", 1L))
    storage.queueStorage.persistQueue(Queue("q2", 2L))
    storage.queueStorage.persistQueue(Queue("q3", 3L))

    // When
    val lookupResult = storage.queueStorage.lookupQueue("q2")

    // Then
    lookupResult must be (Some(Queue("q2", 2L)))
  }

  test("queues should be removed") {
    // Given
    storage.queueStorage.persistQueue(Queue("q1", 1L))
    storage.queueStorage.persistQueue(Queue("q2", 2L))

    // When
    storage.queueStorage.removeQueue(Queue("q1", 1L))

    // Then
    storage.queueStorage.lookupQueue("q1") must be (None)
    storage.queueStorage.lookupQueue("q2") must be (Some(Queue("q2", 2L)))
  }

  test("removing a queue should remove all messages") {
    // Given
    val q1: Queue = Queue("q1", 1L)
    storage.queueStorage.persistQueue(q1)
    storage.messageStorage.persistMessage(Message(q1, "xyz", "123", 10L, 500L))

    // When
    storage.queueStorage.removeQueue(q1)

    // Then
    storage.queueStorage.lookupQueue("q1") must be (None)
    storage.messageStorage.lookupMessage("xyz") must be (None)
  }

  test("updating a queue") {
    // Given
    storage.queueStorage.persistQueue(Queue("q1", 1L));

    // When
    storage.queueStorage.updateQueue(Queue("q1", 100L))

    // Then
    storage.queueStorage.lookupQueue("q1") must be (Some(Queue("q1", 100L)))
  }
}

class MessageStorageTestSuite extends StorageTestSuite {
  test("non-existent message should not be found") {
    // When
    val lookupResult = storage.messageStorage.lookupMessage("xyz")

    // Then
    lookupResult must be (None)
  }

  test("after persisting a message it should be found") {
    // Given
    val q1: Queue = Queue("q1", 1L)
    storage.queueStorage.persistQueue(q1)
    storage.messageStorage.persistMessage(Message(q1, "xyz", "123", 10L, 500L))

    // When
    val lookupResult = storage.messageStorage.lookupMessage("xyz")

    // Then
    lookupResult must be (Some(Message(q1, "xyz", "123", 10L, 500L)))
  }

  test("no undelivered message should not be found in an empty queue") {
    // Given
    val q1: Queue = Queue("q1", 1L)
    val q2: Queue = Queue("q2", 2L)

    storage.queueStorage.persistQueue(q1)
    storage.queueStorage.persistQueue(q2)

    storage.messageStorage.persistMessage(Message(q1, "xyz", "123", 10L, 500L))

    // When
    val lookupResult = storage.messageStorage.lookupUndeliveredMessage(q2)

    // Then
    lookupResult must be (None)
  }

  test("undelivered message should be found in a non-empty queue") {
    // Given
    val q1: Queue = Queue("q1", 1L)
    val q2: Queue = Queue("q2", 2L)

    storage.queueStorage.persistQueue(q1)
    storage.queueStorage.persistQueue(q2)

    storage.messageStorage.persistMessage(Message(q1, "xyz", "123", 10L, 500L))

    // When
    val lookupResult = storage.messageStorage.lookupUndeliveredMessage(q1)

    // Then
    lookupResult must be (Some(Message(q1, "xyz", "123", 10L, 500L)))
  }

  test("updating a message") {
    // Given
    val q1 = Queue("q1", 1L)
    storage.queueStorage.persistQueue(q1)
    storage.messageStorage.persistMessage(Message(q1, "xyz", "123", 10L, 500L))

    // When
    storage.messageStorage.updateMessage(Message(q1, "xyz", "1234", 11L, 501L))

    // Then
    storage.messageStorage.lookupMessage("xyz") must be (Some(Message(q1, "xyz", "1234", 11L, 501L)))
  }
}