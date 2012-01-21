package org.elasticmq.storage

import inmemory.{InMemoryStorageModelModule, InMemoryMessageStatisticsStorageModule, InMemoryMessageStorageModule, InMemoryQueueStorageModule}
import org.scalatest.matchers.MustMatchers
import org.scalatest._
import org.elasticmq._
import org.squeryl.adapters.H2Adapter
import org.elasticmq.storage.squeryl._
import org.joda.time.{Duration, DateTime}

trait StorageTestSuite extends FunSuite with MustMatchers with OneInstancePerTest {
  private case class StorageTestSetup(storageName: String,
                                      initialize: () => MessageStorageModule with QueueStorageModule with MessageStatisticsStorageModule,
                                      shutdown: () => Unit)

  val squerylEnv =
    new SquerylInitializerModule
      with SquerylMessageStorageModule
      with SquerylQueueStorageModule
      with SquerylMessageStatisticsStorageModule
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
      () => squerylEnv.shutdownSqueryl(squerylDBConfiguration.drop)) ::
    StorageTestSetup("In memory",
      () => {
        new InMemoryQueueStorageModule with
          InMemoryMessageStorageModule with
          InMemoryMessageStatisticsStorageModule with
          InMemoryStorageModelModule
      },
      () => ()) :: Nil

  private var _queueStorage: QueueStorageModule#QueueStorage = null
  private var _messageStorage: MessageStorageModule#MessageStorage = null
  private var _messageStatisticsStorage: MessageStatisticsStorageModule#MessageStatisticsStorage = null

  private var _befores: List[() => Unit] = Nil

  def before(block: => Unit) {
    _befores = (() => block) :: _befores
  }

  abstract override protected def test(testName: String, testTags: Tag*)(testFun: => Unit) {
    for (setup <- setups) {
      super.test(testName+" using "+setup.storageName, testTags: _*) {
        val storages = setup.initialize()
        _queueStorage = storages.queueStorage
        _messageStorage = storages.messageStorage
        _messageStatisticsStorage = storages.messageStatisticsStorage
        try {
          _befores.foreach(_())
          testFun
        } finally {
          setup.shutdown()
        }
      }
    }
  }

  def queueStorage = _queueStorage
  def messageStorage = _messageStorage
  def messageStatisticsStorage = _messageStatisticsStorage
}

class QueueStorageTestSuite extends StorageTestSuite {
  test("non-existent queue should not be found") {
    // Given
    queueStorage.persistQueue(Queue("q1", MillisVisibilityTimeout(10L)))

    // When
    val lookupResult = queueStorage.lookupQueue("q2")

    // Then
    lookupResult must be (None)
  }

  test("after persisting a queue it should be found") {
    // Given
    queueStorage.persistQueue(Queue("q1", MillisVisibilityTimeout(1L)))
    queueStorage.persistQueue(Queue("q2", MillisVisibilityTimeout(2L)))
    queueStorage.persistQueue(Queue("q3", MillisVisibilityTimeout(3L)))

    // When
    val lookupResult = queueStorage.lookupQueue("q2")

    // Then
    lookupResult must be (Some(Queue("q2", MillisVisibilityTimeout(2L))))
  }

  test("queue modified and created dates should be stored") {
    // Given
    val created = new DateTime(1216168602L)
    val lastModified = new DateTime(1316168602L)
    queueStorage.persistQueue(Queue("q1", MillisVisibilityTimeout(1L), Duration.ZERO, created, lastModified))

    // When
    val lookupResult = queueStorage.lookupQueue("q1")

    // Then
    lookupResult must be (Some(Queue("q1", MillisVisibilityTimeout(1L), Duration.ZERO, created, lastModified)))
  }

  test("queues should be deleted") {
    // Given
    queueStorage.persistQueue(Queue("q1", MillisVisibilityTimeout(1L)))
    queueStorage.persistQueue(Queue("q2", MillisVisibilityTimeout(2L)))

    // When
    queueStorage.deleteQueue(Queue("q1", MillisVisibilityTimeout(1L)))

    // Then
    queueStorage.lookupQueue("q1") must be (None)
    queueStorage.lookupQueue("q2") must be (Some(Queue("q2", MillisVisibilityTimeout(2L))))
  }

  test("deleting a queue should remove all messages") {
    // Given
    val q1: Queue = Queue("q1", MillisVisibilityTimeout(1L))
    queueStorage.persistQueue(q1)
    messageStorage.persistMessage(Message(q1, "xyz", "123", MillisNextDelivery(123L)))

    // When
    queueStorage.deleteQueue(q1)

    // Then
    queueStorage.lookupQueue("q1") must be (None)
    // TODO: clarify storage contract
    try {
      messageStorage.lookupMessage(q1, "xyz") must be (None)
    } catch {
      case _: IllegalStateException => // ok
    }
  }

  test("updating a queue") {
    // Given
    queueStorage.persistQueue(Queue("q1", MillisVisibilityTimeout(1L)));

    // When
    queueStorage.updateQueue(Queue("q1", MillisVisibilityTimeout(100L)))

    // Then
    queueStorage.lookupQueue("q1") must be (Some(Queue("q1", MillisVisibilityTimeout(100L))))
  }

  test("listing queues") {
    // Given
    queueStorage.persistQueue(Queue("q1", MillisVisibilityTimeout(1L)));
    queueStorage.persistQueue(Queue("q2", MillisVisibilityTimeout(2L)));

    // When
    val queues = queueStorage.listQueues

    // Then
    queues.size must be (2)
    queues(0) must be (Queue("q1", MillisVisibilityTimeout(1L)))
    queues(1) must be (Queue("q2", MillisVisibilityTimeout(2L)))
  }

  test("queue statistics without messages") {
    // Given
    val queue = Queue("q1", MillisVisibilityTimeout(1L))
    queueStorage.persistQueue(queue);

    // When
    val stats = queueStorage.queueStatistics(queue, 123L)

    // Then
    stats must be (QueueStatistics(queue, 0L, 0L, 0L))
  }

  test("queue statistics with messages") {
    // Given
    val queue = Queue("q1", MillisVisibilityTimeout(1L))
    queueStorage.persistQueue(queue);

    // Visible messages
    messageStorage.persistMessage(Message(queue, "m1", "123", MillisNextDelivery(122L)))
    messageStorage.persistMessage(Message(queue, "m2", "123", MillisNextDelivery(123L)))

    // Invisible messages - already received
    val m3 = Message(queue, "m3", "123", MillisNextDelivery(124L)); messageStorage.persistMessage(m3)
    val m4 = Message(queue, "m4", "123", MillisNextDelivery(125L)); messageStorage.persistMessage(m4)
    val m5 = Message(queue, "m5", "123", MillisNextDelivery(126L)); messageStorage.persistMessage(m5)
    val m6 = Message(queue, "m6", "123", MillisNextDelivery(126L)); messageStorage.persistMessage(m6)

    messageStatisticsStorage.writeMessageStatistics(MessageStatistics(m3, OnDateTimeReceived(new DateTime(100L)), 1))

    // Stats are inserted if the counter is 1, updated if it's more than 1. So we first have to insert a row with 1.
    messageStatisticsStorage.writeMessageStatistics(MessageStatistics(m4, OnDateTimeReceived(new DateTime(101L)), 1))
    messageStatisticsStorage.writeMessageStatistics(MessageStatistics(m4, OnDateTimeReceived(new DateTime(102L)), 2))

    messageStatisticsStorage.writeMessageStatistics(MessageStatistics(m5, OnDateTimeReceived(new DateTime(102L)), 1))
    messageStatisticsStorage.writeMessageStatistics(MessageStatistics(m5, OnDateTimeReceived(new DateTime(104L)), 3))

    messageStatisticsStorage.writeMessageStatistics(MessageStatistics(m6, OnDateTimeReceived(new DateTime(103L)), 1))

    // Delayed messages - never yet received
    val m7 = Message(queue, "m7", "123", MillisNextDelivery(127L)); messageStorage.persistMessage(m7)
    val m8 = Message(queue, "m8", "123", MillisNextDelivery(128L)); messageStorage.persistMessage(m8)
    val m9 = Message(queue, "m9", "123", MillisNextDelivery(129L)); messageStorage.persistMessage(m9)

    // When
    val stats = queueStorage.queueStatistics(queue, 123L)

    // Then
    stats must be (QueueStatistics(queue, 2L, 4L, 3L))
  }
}

class MessageStorageTestSuite extends StorageTestSuite {
  test("non-existent message should not be found") {
    // Given
    val q1 = Queue("q1", MillisVisibilityTimeout(1L))
    queueStorage.persistQueue(q1)

    // When
    val lookupResult = messageStorage.lookupMessage(q1, "xyz")

    // Then
    lookupResult must be (None)
  }

  test("after persisting a message it should be found") {
    // Given
    val created = new DateTime(1216168602L)
    val q1: Queue = Queue("q1", MillisVisibilityTimeout(1L))
    val message = Message(q1, "xyz", "123", MillisNextDelivery(123L)).copy(created = created)
    queueStorage.persistQueue(q1)
    messageStorage.persistMessage(message)

    // When
    val lookupResult = messageStorage.lookupMessage(q1, "xyz")

    // Then
    lookupResult must be (Some(message))
  }

  test("sending message with maximum size should succeed") {
    // Given
    val maxMessageContent = "x" * 65535

    val q1: Queue = Queue("q1", MillisVisibilityTimeout(1L))
    queueStorage.persistQueue(q1)
    messageStorage.persistMessage(Message(q1, "xyz", maxMessageContent, MillisNextDelivery(123L)))

    // When
    val lookupResult = messageStorage.lookupMessage(q1, "xyz")

    // Then
    lookupResult must be (Some(Message(q1, "xyz", maxMessageContent, MillisNextDelivery(123L))))
  }

  test("no undelivered message should not be found in an empty queue") {
    // Given
    val q1: Queue = Queue("q1", MillisVisibilityTimeout(1L))
    val q2: Queue = Queue("q2", MillisVisibilityTimeout(2L))

    queueStorage.persistQueue(q1)
    queueStorage.persistQueue(q2)

    messageStorage.persistMessage(Message(q1, "xyz", "123", MillisNextDelivery(123L)))

    // When
    val lookupResult = messageStorage.receiveMessage(q2, 1000L, MillisNextDelivery(234L))

    // Then
    lookupResult must be (None)
  }

  test("undelivered message should be found in a non-empty queue") {
    // Given
    val q1: Queue = Queue("q1", MillisVisibilityTimeout(1L))
    val q2: Queue = Queue("q2", MillisVisibilityTimeout(2L))

    queueStorage.persistQueue(q1)
    queueStorage.persistQueue(q2)

    messageStorage.persistMessage(Message(q1, "xyz", "123", MillisNextDelivery(123L)))

    // When
    val lookupResult = messageStorage.receiveMessage(q1, 200L, MillisNextDelivery(234L))

    // Then
    lookupResult must be (Some(Message(q1, "xyz", "123", MillisNextDelivery(234L))))
  }

  test("next delivery should be updated after receiving") {
    // Given
    val q1: Queue = Queue("q1", MillisVisibilityTimeout(1L))

    queueStorage.persistQueue(q1)

    messageStorage.persistMessage(Message(q1, "xyz", "123", MillisNextDelivery(123L)))

    // When
    messageStorage.receiveMessage(q1, 200L, MillisNextDelivery(567L))
    val lookupResult = messageStorage.lookupMessage(q1, "xyz")

    // Then
    lookupResult must be (Some(Message(q1, "xyz", "123", MillisNextDelivery(567L))))
  }

  test("delivered message should not be found in a non-empty queue when it is not visible") {
    // Given
    val q1: Queue = Queue("q1", MillisVisibilityTimeout(1L))

    queueStorage.persistQueue(q1)

    messageStorage.persistMessage(Message(q1, "xyz", "123", MillisNextDelivery(123L)))

    // When
    val lookupResult = messageStorage.receiveMessage(q1, 100L, MillisNextDelivery(234L))

    // Then
    lookupResult must be (None)
  }

  test("updating a message") {
    // Given
    val q1 = Queue("q1", MillisVisibilityTimeout(1L))
    queueStorage.persistQueue(q1)
    
    val m = Message(q1, "xyz", "1234", MillisNextDelivery(123L))
    messageStorage.persistMessage(m)

    // When
    messageStorage.updateVisibilityTimeout(m, MillisNextDelivery(345L))

    // Then
    messageStorage.lookupMessage(q1, "xyz") must be (Some(Message(q1, "xyz", "1234", MillisNextDelivery(345L))))
  }

  test("message should be deleted") {
    // Given
    val q1 = Queue("q1", MillisVisibilityTimeout(1L))
    val m1 = Message(q1, "xyz", "123", MillisNextDelivery(123L))

    queueStorage.persistQueue(q1)
    messageStorage.persistMessage(m1)

    // When
    messageStorage.deleteMessage(m1)

    // Then
    messageStorage.lookupMessage(q1, "xyz") must be (None)
  }
}

class MessageStatisticsStorageTestSuite extends StorageTestSuite {
  val q1 = Queue("q1", MillisVisibilityTimeout(1L))
  val m1 = Message(q1, "xyz", "123", MillisNextDelivery(123L))

  val someTimestamp = 123456789L;

  before {
    queueStorage.persistQueue(q1)
    messageStorage.persistMessage(m1)
  }

  test("empty statistics should be returned for a non-delivered message") {
    // When
    val stats = messageStatisticsStorage.readMessageStatistics(m1)

    // Then
    stats.approximateFirstReceive must be (NeverReceived)
    stats.approximateReceiveCount must be (0)
    stats.message must be (m1)
  }

  test("statistics should be correct after receiving a message once") {
    // Given
    messageStatisticsStorage.writeMessageStatistics(MessageStatistics(m1, OnDateTimeReceived(new DateTime(someTimestamp)), 1))

    // When
    val readStats = messageStatisticsStorage.readMessageStatistics(m1)

    // Then
    readStats.approximateFirstReceive must be (OnDateTimeReceived(new DateTime(someTimestamp)))
    readStats.approximateReceiveCount must be (1)
    readStats.message must be (m1)
  }

  test("statistics should be correct after receiving a message twice") {
    // Given
    messageStatisticsStorage.writeMessageStatistics(MessageStatistics(m1, OnDateTimeReceived(new DateTime(someTimestamp)), 1))
    messageStatisticsStorage.writeMessageStatistics(MessageStatistics(m1, OnDateTimeReceived(new DateTime(someTimestamp)), 2))

    // When
    val readStats = messageStatisticsStorage.readMessageStatistics(m1)

    // Then
    readStats.approximateFirstReceive must be (OnDateTimeReceived(new DateTime(someTimestamp)))
    readStats.approximateReceiveCount must be (2)
    readStats.message must be (m1)
  }

  test("statistics should be removed if the message is removed") {
    // Given
    messageStatisticsStorage.writeMessageStatistics(MessageStatistics(m1, OnDateTimeReceived(new DateTime(someTimestamp)), 1))
    messageStorage.deleteMessage(m1)

    // When
    val stats = messageStatisticsStorage.readMessageStatistics(m1)

    // Then
    stats.approximateReceiveCount must be (0)
  }

  test("statistics shouldn't be written if the message is already deleted") {
    // Given
    messageStorage.deleteMessage(m1)
    messageStatisticsStorage.writeMessageStatistics(MessageStatistics(m1, OnDateTimeReceived(new DateTime(someTimestamp)), 1))

    // When
    val stats = messageStatisticsStorage.readMessageStatistics(m1)

    // Then
    stats.approximateReceiveCount must be (0)
  }
}