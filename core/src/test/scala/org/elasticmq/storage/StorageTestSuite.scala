package org.elasticmq.storage

import org.scalatest.matchers.MustMatchers
import org.scalatest._
import org.elasticmq._
import squeryl.SquerylStorage
import org.squeryl.adapters.H2Adapter

trait StorageTestSuite extends FunSuite with MustMatchers with OneInstancePerTest {
  private case class StorageTestSetup(storageName: String, storage: Storage, initialize: () => Unit, shutdown: () => Unit)

  private val setups: List[StorageTestSetup] =
    StorageTestSetup("Squeryl", new SquerylStorage,
      () => SquerylStorage.initialize(new H2Adapter, "jdbc:h2:mem:"+this.getClass.getName+";DB_CLOSE_DELAY=-1", "org.h2.Driver"),
      () => SquerylStorage.shutdown(true)) :: Nil

  private var _storage: Storage = null

  abstract override protected def test(testName: String, testTags: Tag*)(testFun: => Unit) {
    for (setup <- setups) {
      super.test(testName+" using "+setup.storageName, testTags: _*) {
        _storage = setup.storage
        setup.initialize()
        try {
          testFun
        } finally {
          setup.shutdown()
        }
      }
    }
  }

  def storage: Storage = _storage
}

class QueueStorageTestSuite extends StorageTestSuite {
  test("non-existent queue should not be found") {
    // Given
    storage.queueStorage.persistQueue(Queue("q1", MillisVisibilityTimeout(10L)))

    // When
    val lookupResult = storage.queueStorage.lookupQueue("q2")

    // Then
    lookupResult must be (None)
  }

  test("after persisting a queue it should be found") {
    // Given
    storage.queueStorage.persistQueue(Queue("q1", MillisVisibilityTimeout(1L)))
    storage.queueStorage.persistQueue(Queue("q2", MillisVisibilityTimeout(2L)))
    storage.queueStorage.persistQueue(Queue("q3", MillisVisibilityTimeout(3L)))

    // When
    val lookupResult = storage.queueStorage.lookupQueue("q2")

    // Then
    lookupResult must be (Some(Queue("q2", MillisVisibilityTimeout(2L))))
  }

  test("queues should be deleted") {
    // Given
    storage.queueStorage.persistQueue(Queue("q1", MillisVisibilityTimeout(1L)))
    storage.queueStorage.persistQueue(Queue("q2", MillisVisibilityTimeout(2L)))

    // When
    storage.queueStorage.deleteQueue(Queue("q1", MillisVisibilityTimeout(1L)))

    // Then
    storage.queueStorage.lookupQueue("q1") must be (None)
    storage.queueStorage.lookupQueue("q2") must be (Some(Queue("q2", MillisVisibilityTimeout(2L))))
  }

  test("deleting a queue should remove all messages") {
    // Given
    val q1: Queue = Queue("q1", MillisVisibilityTimeout(1L))
    storage.queueStorage.persistQueue(q1)
    storage.messageStorage.persistMessage(Message(q1, "xyz", "123", MillisVisibilityTimeout(10L), 500L))

    // When
    storage.queueStorage.deleteQueue(q1)

    // Then
    storage.queueStorage.lookupQueue("q1") must be (None)
    storage.messageStorage.lookupMessage("xyz") must be (None)
  }

  test("updating a queue") {
    // Given
    storage.queueStorage.persistQueue(Queue("q1", MillisVisibilityTimeout(1L)));

    // When
    storage.queueStorage.updateQueue(Queue("q1", MillisVisibilityTimeout(100L)))

    // Then
    storage.queueStorage.lookupQueue("q1") must be (Some(Queue("q1", MillisVisibilityTimeout(100L))))
  }

  test("listing queues") {
    // Given
    storage.queueStorage.persistQueue(Queue("q1", MillisVisibilityTimeout(1L)));
    storage.queueStorage.persistQueue(Queue("q2", MillisVisibilityTimeout(2L)));

    // When
    val queues = storage.queueStorage.listQueues

    // Then
    queues.size must be (2)
    queues(0) must be (Queue("q1", MillisVisibilityTimeout(1L)))
    queues(1) must be (Queue("q2", MillisVisibilityTimeout(2L)))
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
    val q1: Queue = Queue("q1", MillisVisibilityTimeout(1L))
    storage.queueStorage.persistQueue(q1)
    storage.messageStorage.persistMessage(Message(q1, "xyz", "123", MillisVisibilityTimeout(10L), 500L))

    // When
    val lookupResult = storage.messageStorage.lookupMessage("xyz")

    // Then
    lookupResult must be (Some(Message(q1, "xyz", "123", MillisVisibilityTimeout(10L), 500L)))
  }

  test("no undelivered message should not be found in an empty queue") {
    // Given
    val q1: Queue = Queue("q1", MillisVisibilityTimeout(1L))
    val q2: Queue = Queue("q2", MillisVisibilityTimeout(2L))

    storage.queueStorage.persistQueue(q1)
    storage.queueStorage.persistQueue(q2)

    storage.messageStorage.persistMessage(Message(q1, "xyz", "123", MillisVisibilityTimeout(10L), 500L))

    // When
    val lookupResult = storage.messageStorage.lookupPendingMessage(q2, 1000L)

    // Then
    lookupResult must be (None)
  }

  test("undelivered message should be found in a non-empty queue") {
    // Given
    val q1: Queue = Queue("q1", MillisVisibilityTimeout(1L))
    val q2: Queue = Queue("q2", MillisVisibilityTimeout(2L))

    storage.queueStorage.persistQueue(q1)
    storage.queueStorage.persistQueue(q2)

    storage.messageStorage.persistMessage(Message(q1, "xyz", "123", MillisVisibilityTimeout(10L), 40L))

    // When
    val lookupResult = storage.messageStorage.lookupPendingMessage(q1, 100L)

    // Then
    lookupResult must be (Some(Message(q1, "xyz", "123", MillisVisibilityTimeout(10L), 40L)))
  }

  test("delivered message should be found in a non-empty queue when it is visible") {
    // Given
    val q1: Queue = Queue("q1", MillisVisibilityTimeout(1L))

    storage.queueStorage.persistQueue(q1)

    storage.messageStorage.persistMessage(Message(q1, "xyz", "123", MillisVisibilityTimeout(10L), 1234L))

    // When
    val lookupResult = storage.messageStorage.lookupPendingMessage(q1, 5678L)

    // Then
    lookupResult must be (Some(Message(q1, "xyz", "123", MillisVisibilityTimeout(10L), 1234L)))
  }

  test("delivered message should not be found in a non-empty queue when it is not visible") {
    // Given
    val q1: Queue = Queue("q1", MillisVisibilityTimeout(1L))

    storage.queueStorage.persistQueue(q1)

    storage.messageStorage.persistMessage(Message(q1, "xyz", "123", MillisVisibilityTimeout(10L), 5669L))

    // When
    val lookupResult = storage.messageStorage.lookupPendingMessage(q1, 5678L)

    // Then
    lookupResult must be (None)
  }

  test("updating a message") {
    // Given
    val q1 = Queue("q1", MillisVisibilityTimeout(1L))
    storage.queueStorage.persistQueue(q1)
    storage.messageStorage.persistMessage(Message(q1, "xyz", "123", MillisVisibilityTimeout(10L), 500L))

    // When
    storage.messageStorage.updateMessage(Message(q1, "xyz", "1234", MillisVisibilityTimeout(11L), 501L))

    // Then
    storage.messageStorage.lookupMessage("xyz") must be (Some(Message(q1, "xyz", "1234", MillisVisibilityTimeout(11L), 501L)))
  }

  test("updating last delivered should succeed for unchanged message") {
    // Given
    val q1 = Queue("q1", MillisVisibilityTimeout(1L))
    val m1 = Message(q1, "xyz", "123", MillisVisibilityTimeout(10L), 500L)

    storage.queueStorage.persistQueue(q1)
    storage.messageStorage.persistMessage(m1)

    // When
    val updatedMessage = storage.messageStorage.updateLastDelivered(m1, 600L)

    // Then
    val m2 = Message(q1, "xyz", "123", MillisVisibilityTimeout(10L), 600L)

    storage.messageStorage.lookupMessage("xyz") must be (Some(m2))
    updatedMessage must be (Some(m2))
  }

  test("updating last delivered should fail for changed message") {
    // Given
    val q1 = Queue("q1", MillisVisibilityTimeout(1L))
    val m1 = Message(q1, "xyz", "123", MillisVisibilityTimeout(10L), 500L)

    storage.queueStorage.persistQueue(q1)
    storage.messageStorage.persistMessage(m1)

    // When
    val updatedMessage = storage.messageStorage.updateLastDelivered(Message(q1, "xyz", "123", MillisVisibilityTimeout(10L), 550L), 600L)

    // Then
    storage.messageStorage.lookupMessage("xyz") must be (Some(m1))
    updatedMessage must be (None)
  }

  test("message should be deleted") {
    // Given
    val q1 = Queue("q1", MillisVisibilityTimeout(1L))
    val m1 = Message(q1, "xyz", "123", MillisVisibilityTimeout(10L), 500L)

    storage.queueStorage.persistQueue(q1)
    storage.messageStorage.persistMessage(m1)

    // When
    storage.messageStorage.deleteMessage(m1)

    // Then
    storage.messageStorage.lookupMessage("xyz") must be (None)
  }
}