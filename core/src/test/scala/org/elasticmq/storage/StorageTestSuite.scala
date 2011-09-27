package org.elasticmq.storage

import org.scalatest.matchers.MustMatchers
import org.scalatest._
import org.elasticmq._
import org.squeryl.adapters.H2Adapter
import org.elasticmq.storage.squeryl.{SquerylSchemaModule, SquerylQueueStorageModule, SquerylMessageStorageModule, SquerylInitializerModule}

trait StorageTestSuite extends FunSuite with MustMatchers with OneInstancePerTest {
  private case class StorageTestSetup(storageName: String,
                                      initialize: () => MessageStorageModule with QueueStorageModule,
                                      shutdown: () => Unit)

  val squerylEnv =
    new SquerylInitializerModule
      with SquerylMessageStorageModule
      with SquerylQueueStorageModule
      with SquerylSchemaModule

  val squerylDBConfiguration = DBConfiguration(new H2Adapter,
    "jdbc:h2:mem:"+this.getClass.getName+";DB_CLOSE_DELAY=-1",
    "org.h2.Driver")

  private val setups: List[StorageTestSetup] =
    StorageTestSetup("Squeryl",
      () => {
        squerylEnv.initializeSqueryl(squerylDBConfiguration);
        squerylEnv
      },
      () => squerylEnv.shutdownSqueryl(squerylDBConfiguration.drop)) :: Nil

  private var _queueStorage: QueueStorageModule#QueueStorage = null
  private var _messageStorage: MessageStorageModule#MessageStorage = null

  abstract override protected def test(testName: String, testTags: Tag*)(testFun: => Unit) {
    for (setup <- setups) {
      super.test(testName+" using "+setup.storageName, testTags: _*) {
        val storages = setup.initialize()
        _queueStorage = storages.queueStorage
        _messageStorage = storages.messageStorage
        try {
          testFun
        } finally {
          setup.shutdown()
        }
      }
    }
  }

  def queueStorage = _queueStorage
  def messageStorage = _messageStorage
}

class QueueStorageTestSuite extends StorageTestSuite {
  test("non-existent queue should not be found") {
    // Given
    queueStorage.persistQueue(Queue("q1", VisibilityTimeout(10L)))

    // When
    val lookupResult = queueStorage.lookupQueue("q2")

    // Then
    lookupResult must be (None)
  }

  test("after persisting a queue it should be found") {
    // Given
    queueStorage.persistQueue(Queue("q1", VisibilityTimeout(1L)))
    queueStorage.persistQueue(Queue("q2", VisibilityTimeout(2L)))
    queueStorage.persistQueue(Queue("q3", VisibilityTimeout(3L)))

    // When
    val lookupResult = queueStorage.lookupQueue("q2")

    // Then
    lookupResult must be (Some(Queue("q2", VisibilityTimeout(2L))))
  }

  test("queues should be deleted") {
    // Given
    queueStorage.persistQueue(Queue("q1", VisibilityTimeout(1L)))
    queueStorage.persistQueue(Queue("q2", VisibilityTimeout(2L)))

    // When
    queueStorage.deleteQueue(Queue("q1", VisibilityTimeout(1L)))

    // Then
    queueStorage.lookupQueue("q1") must be (None)
    queueStorage.lookupQueue("q2") must be (Some(Queue("q2", VisibilityTimeout(2L))))
  }

  test("deleting a queue should remove all messages") {
    // Given
    val q1: Queue = Queue("q1", VisibilityTimeout(1L))
    queueStorage.persistQueue(q1)
    messageStorage.persistMessage(Message(q1, "xyz", "123", MillisNextDelivery(123L)))

    // When
    queueStorage.deleteQueue(q1)

    // Then
    queueStorage.lookupQueue("q1") must be (None)
    messageStorage.lookupMessage("xyz") must be (None)
  }

  test("updating a queue") {
    // Given
    queueStorage.persistQueue(Queue("q1", VisibilityTimeout(1L)));

    // When
    queueStorage.updateQueue(Queue("q1", VisibilityTimeout(100L)))

    // Then
    queueStorage.lookupQueue("q1") must be (Some(Queue("q1", VisibilityTimeout(100L))))
  }

  test("listing queues") {
    // Given
    queueStorage.persistQueue(Queue("q1", VisibilityTimeout(1L)));
    queueStorage.persistQueue(Queue("q2", VisibilityTimeout(2L)));

    // When
    val queues = queueStorage.listQueues

    // Then
    queues.size must be (2)
    queues(0) must be (Queue("q1", VisibilityTimeout(1L)))
    queues(1) must be (Queue("q2", VisibilityTimeout(2L)))
  }
}

class MessageStorageTestSuite extends StorageTestSuite {
  test("non-existent message should not be found") {
    // When
    val lookupResult = messageStorage.lookupMessage("xyz")

    // Then
    lookupResult must be (None)
  }

  test("after persisting a message it should be found") {
    // Given
    val q1: Queue = Queue("q1", VisibilityTimeout(1L))
    queueStorage.persistQueue(q1)
    messageStorage.persistMessage(Message(q1, "xyz", "123", MillisNextDelivery(123L)))

    // When
    val lookupResult = messageStorage.lookupMessage("xyz")

    // Then
    lookupResult must be (Some(Message(q1, "xyz", "123", MillisNextDelivery(123L))))
  }

  test("sending message with maximum size should succeed") {
    // Given
    val maxMessageContent = "x" * 65536

    val q1: Queue = Queue("q1", VisibilityTimeout(1L))
    queueStorage.persistQueue(q1)
    messageStorage.persistMessage(Message(q1, "xyz", maxMessageContent, MillisNextDelivery(123L)))

    // When
    val lookupResult = messageStorage.lookupMessage("xyz")

    // Then
    lookupResult must be (Some(Message(q1, "xyz", maxMessageContent, MillisNextDelivery(123L))))
  }

  test("no undelivered message should not be found in an empty queue") {
    // Given
    val q1: Queue = Queue("q1", VisibilityTimeout(1L))
    val q2: Queue = Queue("q2", VisibilityTimeout(2L))

    queueStorage.persistQueue(q1)
    queueStorage.persistQueue(q2)

    messageStorage.persistMessage(Message(q1, "xyz", "123", MillisNextDelivery(123L)))

    // When
    val lookupResult = messageStorage.lookupPendingMessage(q2, 1000L)

    // Then
    lookupResult must be (None)
  }

  test("undelivered message should be found in a non-empty queue") {
    // Given
    val q1: Queue = Queue("q1", VisibilityTimeout(1L))
    val q2: Queue = Queue("q2", VisibilityTimeout(2L))

    queueStorage.persistQueue(q1)
    queueStorage.persistQueue(q2)

    messageStorage.persistMessage(Message(q1, "xyz", "123", MillisNextDelivery(123L)))

    // When
    val lookupResult = messageStorage.lookupPendingMessage(q1, 200L)

    // Then
    lookupResult must be (Some(Message(q1, "xyz", "123", MillisNextDelivery(123L))))
  }

  test("delivered message should not be found in a non-empty queue when it is not visible") {
    // Given
    val q1: Queue = Queue("q1", VisibilityTimeout(1L))

    queueStorage.persistQueue(q1)

    messageStorage.persistMessage(Message(q1, "xyz", "123", MillisNextDelivery(123L)))

    // When
    val lookupResult = messageStorage.lookupPendingMessage(q1, 100L)

    // Then
    lookupResult must be (None)
  }

  test("updating a message") {
    // Given
    val q1 = Queue("q1", VisibilityTimeout(1L))
    queueStorage.persistQueue(q1)
    messageStorage.persistMessage(Message(q1, "xyz", "123", MillisNextDelivery(123L)))

    // When
    messageStorage.updateMessage(Message(q1, "xyz", "1234", MillisNextDelivery(345L)))

    // Then
    messageStorage.lookupMessage("xyz") must be (Some(Message(q1, "xyz", "1234", MillisNextDelivery(345L))))
  }

  test("updating next delivery should succeed for unchanged message") {
    // Given
    val q1 = Queue("q1", VisibilityTimeout(1L))
    val m1 = Message(q1, "xyz", "123", MillisNextDelivery(123L))

    queueStorage.persistQueue(q1)
    messageStorage.persistMessage(m1)

    // When
    val updatedMessage = messageStorage.updateNextDelivery(m1, MillisNextDelivery(600L))

    // Then
    val m2 = Message(q1, "xyz", "123", MillisNextDelivery(600L))

    messageStorage.lookupMessage("xyz") must be (Some(m2))
    updatedMessage must be (Some(m2))
  }

  test("updating last delivered should fail for changed message") {
    // Given
    val q1 = Queue("q1", VisibilityTimeout(1L))
    val m1 = Message(q1, "xyz", "123", MillisNextDelivery(123L))

    queueStorage.persistQueue(q1)
    messageStorage.persistMessage(m1)

    // When
    val updatedMessage = messageStorage.updateNextDelivery(
      Message(q1, "xyz", "123", MillisNextDelivery(345L)),
      MillisNextDelivery(600L))

    // Then
    messageStorage.lookupMessage("xyz") must be (Some(m1))
    updatedMessage must be (None)
  }

  test("message should be deleted") {
    // Given
    val q1 = Queue("q1", VisibilityTimeout(1L))
    val m1 = Message(q1, "xyz", "123", MillisNextDelivery(123L))

    queueStorage.persistQueue(q1)
    messageStorage.persistMessage(m1)

    // When
    messageStorage.deleteMessage(m1)

    // Then
    messageStorage.lookupMessage("xyz") must be (None)
  }
}