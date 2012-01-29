package org.elasticmq.storage

import inmemory._
import org.scalatest.matchers.MustMatchers
import org.scalatest._
import org.elasticmq._
import org.squeryl.adapters.H2Adapter
import org.elasticmq.storage.squeryl._
import org.joda.time.{Duration, DateTime}
import org.elasticmq.impl.{MessageData, QueueData}
import org.elasticmq.test.DataCreationHelpers

trait StorageTestSuite extends FunSuite with MustMatchers with OneInstancePerTest with DataCreationHelpers {
  private case class StorageTestSetup(storageName: String,
                                      initialize: () => StorageModule,
                                      shutdown: () => Unit)

  val squerylEnv = new SquerylStorageModule {}

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
      () => new InMemoryStorageModule {},
      () => ()) :: Nil

  private var storageModule: StorageModule = null

  private var _befores: List[() => Unit] = Nil

  def before(block: => Unit) {
    _befores = (() => block) :: _befores
  }

  abstract override protected def test(testName: String, testTags: Tag*)(testFun: => Unit) {
    for (setup <- setups) {
      super.test(testName+" using "+setup.storageName, testTags: _*) {
        storageModule = setup.initialize()
        try {
          _befores.foreach(_())
          testFun
        } finally {
          setup.shutdown()
        }
      }
    }
  }

  def queueStorage = storageModule.queueStorage
  def messageStorage(queueName: String) = storageModule.messageStorage(queueName)
  def messageStatisticsStorage(queueName: String) = storageModule.messageStatisticsStorage(queueName)
}

class QueueStorageTestSuite extends StorageTestSuite {
  test("non-existent queue should not be found") {
    // Given
    queueStorage.persistQueue(createQueueData("q1", MillisVisibilityTimeout(10L)))

    // When
    val lookupResult = queueStorage.lookupQueue("q2")

    // Then
    lookupResult must be (None)
  }

  test("after persisting a queue it should be found") {
    // Given
    val q2 = createQueueData("q2", MillisVisibilityTimeout(2L))

    queueStorage.persistQueue(createQueueData("q1", MillisVisibilityTimeout(1L)))
    queueStorage.persistQueue(q2)
    queueStorage.persistQueue(createQueueData("q3", MillisVisibilityTimeout(3L)))

    // When
    val lookupResult = queueStorage.lookupQueue(q2.name)

    // Then
    lookupResult must be (Some(q2))
  }

  test("queue modified and created dates should be stored") {
    // Given
    val created = new DateTime(1216168602L)
    val lastModified = new DateTime(1316168602L)
    queueStorage.persistQueue(QueueData("q1", MillisVisibilityTimeout(1L), Duration.ZERO, created, lastModified))

    // When
    val lookupResult = queueStorage.lookupQueue("q1")

    // Then
    lookupResult must be (Some(QueueData("q1", MillisVisibilityTimeout(1L), Duration.ZERO, created, lastModified)))
  }

  test("queues should be deleted") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val q2 = createQueueData("q2", MillisVisibilityTimeout(2L))

    queueStorage.persistQueue(q1)
    queueStorage.persistQueue(q2)

    // When
    queueStorage.deleteQueue(q1.name)

    // Then
    queueStorage.lookupQueue(q1.name) must be (None)
    queueStorage.lookupQueue(q2.name) must be (Some(q2))
  }

  test("deleting a queue should remove all messages") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    queueStorage.persistQueue(q1)

    val m1 = createMessageData("xyz", "123", MillisNextDelivery(123L))
    messageStorage(q1.name).persistMessage(m1)

    // When
    queueStorage.deleteQueue(q1.name)

    // Then
    queueStorage.lookupQueue(q1.name) must be (None)

    // Either result is ok
    try {
      messageStorage(q1.name).lookupMessage(m1.id) must be (None)
    } catch {
      case _: QueueDoesNotExistException => // ok
    }
  }

  test("trying to create an existing queue should throw an exception") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    queueStorage.persistQueue(q1)

    // When & then
    evaluating { queueStorage.persistQueue(q1) } must produce [QueueAlreadyExistsException]
  }

  test("updating a queue") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    queueStorage.persistQueue(q1);

    // When
    val q1Modified = createQueueData(q1.name, MillisVisibilityTimeout(100L))
    queueStorage.updateQueue(q1Modified)

    // Then
    queueStorage.lookupQueue(q1.name) must be (Some(q1Modified))
  }

  test("listing queues") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val q2 = createQueueData("q2", MillisVisibilityTimeout(2L))

    queueStorage.persistQueue(q1);
    queueStorage.persistQueue(q2);

    // When
    val queues = queueStorage.listQueues

    // Then
    queues.size must be (2)
    queues(0) must be (q1)
    queues(1) must be (q2)
  }

  test("queue statistics without messages") {
    // Given
    val queue = createQueueData("q1", MillisVisibilityTimeout(1L))
    queueStorage.persistQueue(queue);

    // When
    val stats = queueStorage.queueStatistics(queue.name, 123L)

    // Then
    stats must be (QueueStatistics(0L, 0L, 0L))
  }

  test("queue statistics with messages") {
    // Given
    val queue = createQueueData("q1", MillisVisibilityTimeout(1L))
    queueStorage.persistQueue(queue);

    val msgStorage = messageStorage(queue.name)
    val statsStorage = messageStatisticsStorage(queue.name)

    // Visible messages
    messageStorage(queue.name).persistMessage(createMessageData("m1", "123", MillisNextDelivery(122L)))
    messageStorage(queue.name).persistMessage(createMessageData("m2", "123", MillisNextDelivery(123L)))

    // Invisible messages - already received
    val m3 = createMessageData("m3", "123", MillisNextDelivery(124L)); msgStorage.persistMessage(m3)
    val m4 = createMessageData("m4", "123", MillisNextDelivery(125L)); msgStorage.persistMessage(m4)
    val m5 = createMessageData("m5", "123", MillisNextDelivery(126L)); msgStorage.persistMessage(m5)
    val m6 = createMessageData("m6", "123", MillisNextDelivery(126L)); msgStorage.persistMessage(m6)

    statsStorage.writeMessageStatistics(m3.id, MessageStatistics(OnDateTimeReceived(new DateTime(100L)), 1))

    // Stats are inserted if the counter is 1, updated if it's more than 1. So we first have to insert a row with 1.
    statsStorage.writeMessageStatistics(m4.id, MessageStatistics(OnDateTimeReceived(new DateTime(101L)), 1))
    statsStorage.writeMessageStatistics(m4.id, MessageStatistics(OnDateTimeReceived(new DateTime(102L)), 2))

    statsStorage.writeMessageStatistics(m5.id, MessageStatistics(OnDateTimeReceived(new DateTime(102L)), 1))
    statsStorage.writeMessageStatistics(m5.id, MessageStatistics(OnDateTimeReceived(new DateTime(104L)), 3))

    statsStorage.writeMessageStatistics(m6.id, MessageStatistics(OnDateTimeReceived(new DateTime(103L)), 1))

    // Delayed messages - never yet received
    val m7 = createMessageData("m7", "123", MillisNextDelivery(127L)); msgStorage.persistMessage(m7)
    val m8 = createMessageData("m8", "123", MillisNextDelivery(128L)); msgStorage.persistMessage(m8)
    val m9 = createMessageData("m9", "123", MillisNextDelivery(129L)); msgStorage.persistMessage(m9)

    // When
    val stats = queueStorage.queueStatistics(queue.name, 123L)

    // Then
    stats must be (QueueStatistics(2L, 4L, 3L))
  }
}

class MessageStorageTestSuite extends StorageTestSuite {
  test("non-existent message should not be found") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    queueStorage.persistQueue(q1)

    // When
    val lookupResult = messageStorage(q1.name).lookupMessage(MessageId("xyz"))

    // Then
    lookupResult must be (None)
  }

  test("after persisting a message it should be found") {
    // Given
    val created = new DateTime(1216168602L)
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val message = createMessageData("xyz", "123", MillisNextDelivery(123L)).copy(created = created)
    queueStorage.persistQueue(q1)
    messageStorage(q1.name).persistMessage(message)

    // When
    val lookupResult = messageStorage(q1.name).lookupMessage(MessageId("xyz"))

    // Then
    lookupResult must be (Some(message))
  }

  test("sending message with maximum size should succeed") {
    // Given
    val maxMessageContent = "x" * 65535

    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    queueStorage.persistQueue(q1)
    messageStorage(q1.name).persistMessage(createMessageData("xyz", maxMessageContent, MillisNextDelivery(123L)))

    // When
    val lookupResult = messageStorage(q1.name).lookupMessage(MessageId("xyz"))

    // Then
    lookupResult must be (Some(createMessageData("xyz", maxMessageContent, MillisNextDelivery(123L))))
  }

  test("no undelivered message should not be found in an empty queue") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val q2 = createQueueData("q2", MillisVisibilityTimeout(2L))

    queueStorage.persistQueue(q1)
    queueStorage.persistQueue(q2)

    messageStorage(q1.name).persistMessage(createMessageData("xyz", "123", MillisNextDelivery(123L)))

    // When
    val lookupResult = messageStorage(q2.name).receiveMessage(1000L, MillisNextDelivery(234L))

    // Then
    lookupResult must be (None)
  }

  test("undelivered message should be found in a non-empty queue") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val q2 = createQueueData("q2", MillisVisibilityTimeout(2L))

    queueStorage.persistQueue(q1)
    queueStorage.persistQueue(q2)

    messageStorage(q1.name).persistMessage(createMessageData("xyz", "123", MillisNextDelivery(123L)))

    // When
    val lookupResult = messageStorage(q1.name).receiveMessage(200L, MillisNextDelivery(234L))

    // Then
    lookupResult must be (Some(createMessageData("xyz", "123", MillisNextDelivery(234L))))
  }

  test("next delivery should be updated after receiving") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))

    queueStorage.persistQueue(q1)

    messageStorage(q1.name).persistMessage(createMessageData("xyz", "123", MillisNextDelivery(123L)))

    // When
    messageStorage(q1.name).receiveMessage(200L, MillisNextDelivery(567L))
    val lookupResult = messageStorage(q1.name).lookupMessage(MessageId("xyz"))

    // Then
    lookupResult must be (Some(createMessageData("xyz", "123", MillisNextDelivery(567L))))
  }

  test("delivered message should not be found in a non-empty queue when it is not visible") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))

    queueStorage.persistQueue(q1)

    messageStorage(q1.name).persistMessage(createMessageData("xyz", "123", MillisNextDelivery(123L)))

    // When
    val lookupResult = messageStorage(q1.name).receiveMessage(100L, MillisNextDelivery(234L))

    // Then
    lookupResult must be (None)
  }

  test("updating a message") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    queueStorage.persistQueue(q1)
    
    val m = createMessageData("xyz", "1234", MillisNextDelivery(123L))
    messageStorage(q1.name).persistMessage(m)

    // When
    messageStorage(q1.name).updateVisibilityTimeout(m.id, MillisNextDelivery(345L))

    // Then
    messageStorage(q1.name).lookupMessage(MessageId("xyz")) must be (Some(createMessageData("xyz", "1234", MillisNextDelivery(345L))))
  }

  test("message should be deleted") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val m1 = createMessageData("xyz", "123", MillisNextDelivery(123L))

    queueStorage.persistQueue(q1)
    messageStorage(q1.name).persistMessage(m1)

    // When
    messageStorage(q1.name).deleteMessage(m1.id)

    // Then
    messageStorage(q1.name).lookupMessage(MessageId("xyz")) must be (None)
  }
}

class MessageStatisticsStorageTestSuite extends StorageTestSuite {
  val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
  val m1 = createMessageData("xyz", "123", MillisNextDelivery(123L))

  val someTimestamp = 123456789L;

  before {
    queueStorage.persistQueue(q1)
    messageStorage(q1.name).persistMessage(m1)
  }

  test("empty statistics should be returned for a non-delivered message") {
    // When
    val stats = messageStatisticsStorage(q1.name).readMessageStatistics(m1.id)

    // Then
    stats.approximateFirstReceive must be (NeverReceived)
    stats.approximateReceiveCount must be (0)
  }

  test("statistics should be correct after receiving a message once") {
    // Given
    messageStatisticsStorage(q1.name).writeMessageStatistics(m1.id, MessageStatistics(OnDateTimeReceived(new DateTime(someTimestamp)), 1))

    // When
    val readStats = messageStatisticsStorage(q1.name).readMessageStatistics(m1.id)

    // Then
    readStats.approximateFirstReceive must be (OnDateTimeReceived(new DateTime(someTimestamp)))
    readStats.approximateReceiveCount must be (1)
  }

  test("statistics should be correct after receiving a message twice") {
    // Given
    messageStatisticsStorage(q1.name).writeMessageStatistics(m1.id, MessageStatistics(OnDateTimeReceived(new DateTime(someTimestamp)), 1))
    messageStatisticsStorage(q1.name).writeMessageStatistics(m1.id, MessageStatistics(OnDateTimeReceived(new DateTime(someTimestamp)), 2))

    // When
    val readStats = messageStatisticsStorage(q1.name).readMessageStatistics(m1.id)

    // Then
    readStats.approximateFirstReceive must be (OnDateTimeReceived(new DateTime(someTimestamp)))
    readStats.approximateReceiveCount must be (2)
  }

  test("statistics should be removed if the message is removed") {
    // Given
    messageStatisticsStorage(q1.name).writeMessageStatistics(m1.id, MessageStatistics(OnDateTimeReceived(new DateTime(someTimestamp)), 1))
    messageStorage(q1.name).deleteMessage(m1.id)

    // When & then
    evaluating { messageStatisticsStorage(q1.name).readMessageStatistics(m1.id) } must produce [MessageDoesNotExistException]
  }

  test("statistics shouldn't be written if the message is already deleted") {
    // Given
    messageStorage(q1.name).deleteMessage(m1.id)
    messageStatisticsStorage(q1.name).writeMessageStatistics(m1.id, MessageStatistics(OnDateTimeReceived(new DateTime(someTimestamp)), 1))

    // When & then
    evaluating { messageStatisticsStorage(q1.name).readMessageStatistics(m1.id) } must produce [MessageDoesNotExistException]
  }
}