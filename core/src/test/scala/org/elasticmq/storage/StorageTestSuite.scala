package org.elasticmq.storage

import inmemory._
import org.scalatest.matchers.MustMatchers
import org.scalatest._
import org.elasticmq._
import org.squeryl.adapters.H2Adapter
import org.elasticmq.storage.squeryl._
import org.joda.time.{Duration, DateTime}
import org.elasticmq.data.QueueData
import org.elasticmq.test.DataCreationHelpers

trait StorageTestSuite extends FunSuite with MustMatchers with OneInstancePerTest with DataCreationHelpers {
  private case class StorageTestSetup(storageName: String,
                                      initialize: () => StorageCommandExecutor,
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

  private var storageCommandExecutor: StorageCommandExecutor = null

  private var _befores: List[() => Unit] = Nil

  def before(block: => Unit) {
    _befores = (() => block) :: _befores
  }

  abstract override protected def test(testName: String, testTags: Tag*)(testFun: => Unit) {
    for (setup <- setups) {
      super.test(testName+" using "+setup.storageName, testTags: _*) {
        storageCommandExecutor = setup.initialize()
        try {
          _befores.foreach(_())
          testFun
        } finally {
          setup.shutdown()
        }
      }
    }
  }
  
  def execute[R](command: StorageCommand[R]): R = storageCommandExecutor.execute(command)
}

class QueueCommandsTestSuite extends StorageTestSuite {
  test("non-existent queue should not be found") {
    // Given
    execute(CreateQueueCommand(createQueueData("q1", MillisVisibilityTimeout(10L))))

    // When
    val lookupResult = execute(LookupQueueCommand("q2"))

    // Then
    lookupResult must be (None)
  }

  test("after persisting a queue it should be found") {
    // Given
    val q2 = createQueueData("q2", MillisVisibilityTimeout(2L))

    execute(CreateQueueCommand(createQueueData("q1", MillisVisibilityTimeout(1L))))
    execute(CreateQueueCommand(q2))
    execute(CreateQueueCommand(createQueueData("q3", MillisVisibilityTimeout(3L))))

    // When
    val lookupResult = execute(LookupQueueCommand(q2.name))

    // Then
    lookupResult must be (Some(q2))
  }

  test("queue modified and created dates should be stored") {
    // Given
    val created = new DateTime(1216168602L)
    val lastModified = new DateTime(1316168602L)
    execute(CreateQueueCommand(QueueData("q1", MillisVisibilityTimeout(1L), Duration.ZERO, created, lastModified)))

    // When
    val lookupResult = execute(LookupQueueCommand("q1"))

    // Then
    lookupResult must be (Some(QueueData("q1", MillisVisibilityTimeout(1L), Duration.ZERO, created, lastModified)))
  }

  test("queues should be deleted") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val q2 = createQueueData("q2", MillisVisibilityTimeout(2L))

    execute(CreateQueueCommand(q1))
    execute(CreateQueueCommand(q2))

    // When
    execute(DeleteQueueCommand(q1.name))

    // Then
    execute(LookupQueueCommand(q1.name)) must be (None)
    execute(LookupQueueCommand(q2.name)) must be (Some(q2))
  }

  test("deleting a queue should remove all messages") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    execute(CreateQueueCommand(q1))

    val m1 = createMessageData("xyz", "123", MillisNextDelivery(123L))
    execute(new SendMessageCommand(q1.name, m1))

    // When
    execute(DeleteQueueCommand(q1.name))

    // Then
    execute(LookupQueueCommand(q1.name)) must be (None)

    // Either result is ok
    try {
      execute(LookupMessageCommand(q1.name, m1.id)) must be (None)
    } catch {
      case _: QueueDoesNotExistException => // ok
    }
  }

  test("trying to create an existing queue should throw an exception") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    execute(CreateQueueCommand(q1))

    // When & then
    evaluating { execute(CreateQueueCommand(q1)) } must produce [QueueAlreadyExistsException]
  }

  test("updating a queue") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    execute(CreateQueueCommand(q1))

    // When
    val q1Modified = createQueueData(q1.name, MillisVisibilityTimeout(100L))
    execute(UpdateQueueCommand(q1Modified))

    // Then
    execute(LookupQueueCommand(q1.name)) must be (Some(q1Modified))
  }

  test("listing queues") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val q2 = createQueueData("q2", MillisVisibilityTimeout(2L))

    execute(CreateQueueCommand(q1))
    execute(CreateQueueCommand(q2))

    // When
    val queues = execute(ListQueuesCommand())

    // Then
    queues.size must be (2)
    queues(0) must be (q1)
    queues(1) must be (q2)
  }

  test("queue statistics without messages") {
    // Given
    val queue = createQueueData("q1", MillisVisibilityTimeout(1L))
    execute(CreateQueueCommand(queue))

    // When
    val stats = execute(GetQueueStatisticsCommand(queue.name, 123L))

    // Then
    stats must be (QueueStatistics(0L, 0L, 0L))
  }

  test("queue statistics with messages") {
    // Given
    val queue = createQueueData("q1", MillisVisibilityTimeout(1L))
    execute(CreateQueueCommand(queue))

    // Visible messages
    execute(new SendMessageCommand(queue.name, createMessageData("m1", "123", MillisNextDelivery(122L))))
    execute(new SendMessageCommand(queue.name, createMessageData("m2", "123", MillisNextDelivery(123L))))

    // Invisible messages - already received
    val m3 = createMessageData("m3", "123", MillisNextDelivery(124L)); execute(SendMessageCommand(queue.name, m3))
    val m4 = createMessageData("m4", "123", MillisNextDelivery(125L)); execute(SendMessageCommand(queue.name, m4))
    val m5 = createMessageData("m5", "123", MillisNextDelivery(126L)); execute(SendMessageCommand(queue.name, m5))
    val m6 = createMessageData("m6", "123", MillisNextDelivery(126L)); execute(SendMessageCommand(queue.name, m6))

    execute(UpdateMessageStatisticsCommand(queue.name, m3.id, MessageStatistics(OnDateTimeReceived(new DateTime(100L)), 1)))

    // Stats are inserted if the counter is 1, updated if it's more than 1. So we first have to insert a row with 1.
    execute(UpdateMessageStatisticsCommand(queue.name, m4.id, MessageStatistics(OnDateTimeReceived(new DateTime(101L)), 1)))
    execute(UpdateMessageStatisticsCommand(queue.name, m4.id, MessageStatistics(OnDateTimeReceived(new DateTime(102L)), 2)))

    execute(UpdateMessageStatisticsCommand(queue.name, m5.id, MessageStatistics(OnDateTimeReceived(new DateTime(102L)), 1)))
    execute(UpdateMessageStatisticsCommand(queue.name, m5.id, MessageStatistics(OnDateTimeReceived(new DateTime(104L)), 3)))

    execute(UpdateMessageStatisticsCommand(queue.name, m6.id, MessageStatistics(OnDateTimeReceived(new DateTime(103L)), 1)))

    // Delayed messages - never yet received
    val m7 = createMessageData("m7", "123", MillisNextDelivery(127L)); execute(SendMessageCommand(queue.name, m7))
    val m8 = createMessageData("m8", "123", MillisNextDelivery(128L)); execute(SendMessageCommand(queue.name, m8))
    val m9 = createMessageData("m9", "123", MillisNextDelivery(129L)); execute(SendMessageCommand(queue.name, m9))

    // When
    val stats = execute(GetQueueStatisticsCommand(queue.name, 123L))

    // Then
    stats must be (QueueStatistics(2L, 4L, 3L))
  }
}

class MessageCommandsTestSuite extends StorageTestSuite {
  test("non-existent message should not be found") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    execute(CreateQueueCommand(q1))

    // When    
    val lookupResult = execute(LookupMessageCommand(q1.name, MessageId("xyz")))

    // Then
    lookupResult must be (None)
  }

  test("after persisting a message it should be found") {
    // Given
    val created = new DateTime(1216168602L)
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val message = createMessageData("xyz", "123", MillisNextDelivery(123L)).copy(created = created)
    execute(CreateQueueCommand(q1))
    execute(new SendMessageCommand(q1.name, message))

    // When
    val lookupResult = execute(LookupMessageCommand(q1.name, MessageId("xyz")))

    // Then
    lookupResult must be (Some(message))
  }

  test("sending message with maximum size should succeed") {
    // Given
    val maxMessageContent = "x" * 65535

    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    execute(CreateQueueCommand(q1))
    execute(new SendMessageCommand(q1.name, createMessageData("xyz", maxMessageContent, MillisNextDelivery(123L))))

    // When
    val lookupResult = execute(LookupMessageCommand(q1.name, MessageId("xyz")))

    // Then
    lookupResult must be (Some(createMessageData("xyz", maxMessageContent, MillisNextDelivery(123L))))
  }

  test("no undelivered message should not be found in an empty queue") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val q2 = createQueueData("q2", MillisVisibilityTimeout(2L))

    execute(CreateQueueCommand(q1))
    execute(CreateQueueCommand(q2))

    execute(new SendMessageCommand(q1.name, createMessageData("xyz", "123", MillisNextDelivery(123L))))

    // When
    val lookupResult = execute(ReceiveMessageCommand(q2.name, 1000L, MillisNextDelivery(234L)))

    // Then
    lookupResult must be (None)
  }

  test("undelivered message should be found in a non-empty queue") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val q2 = createQueueData("q2", MillisVisibilityTimeout(2L))

    execute(CreateQueueCommand(q1))
    execute(CreateQueueCommand(q2))

    execute(new SendMessageCommand(q1.name, createMessageData("xyz", "123", MillisNextDelivery(123L))))

    // When
    val lookupResult = execute(ReceiveMessageCommand(q1.name, 200L, MillisNextDelivery(234L)))

    // Then
    lookupResult must be (Some(createMessageData("xyz", "123", MillisNextDelivery(234L))))
  }

  test("next delivery should be updated after receiving") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))

    execute(CreateQueueCommand(q1))

    execute(new SendMessageCommand(q1.name, createMessageData("xyz", "123", MillisNextDelivery(123L))))

    // When
    execute(ReceiveMessageCommand(q1.name, 200L, MillisNextDelivery(567L)))
    val lookupResult = execute(LookupMessageCommand(q1.name, MessageId("xyz")))

    // Then
    lookupResult must be (Some(createMessageData("xyz", "123", MillisNextDelivery(567L))))
  }

  test("delivered message should not be found in a non-empty queue when it is not visible") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))

    execute(CreateQueueCommand(q1))

    execute(new SendMessageCommand(q1.name, createMessageData("xyz", "123", MillisNextDelivery(123L))))

    // When
    val lookupResult = execute(ReceiveMessageCommand(q1.name, 100L, MillisNextDelivery(234L)))

    // Then
    lookupResult must be (None)
  }

  test("increasing visibility timeout of a message") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    execute(CreateQueueCommand(q1))
    
    val m = createMessageData("xyz", "1234", MillisNextDelivery(123L))
    execute(new SendMessageCommand(q1.name, m))

    // When
    execute(UpdateVisibilityTimeoutCommand(q1.name, m.id, MillisNextDelivery(345L)))

    // Then
    execute(LookupMessageCommand(q1.name, MessageId("xyz"))) must be (Some(createMessageData("xyz", "1234", MillisNextDelivery(345L))))
  }

  test("decreasing visibility timeout of a message") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    execute(CreateQueueCommand(q1))

    // Initially m2 should be delivered after m1
    val m1 = createMessageData("xyz1", "1234", MillisNextDelivery(100L))
    val m2 = createMessageData("xyz2", "1234", MillisNextDelivery(200L))

    execute(new SendMessageCommand(q1.name, m1))
    execute(new SendMessageCommand(q1.name, m2))

    // When
    execute(UpdateVisibilityTimeoutCommand(q1.name, m2.id, MillisNextDelivery(50L)))

    // Then
    // This should find the first message, as it has the visibility timeout decreased.
    execute(ReceiveMessageCommand(q1.name, 75L, MillisNextDelivery(100L))).map(_.id) must be (Some(m2.id))
  }

  test("message should be deleted") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val m1 = createMessageData("xyz", "123", MillisNextDelivery(123L))

    execute(CreateQueueCommand(q1))
    execute(new SendMessageCommand(q1.name, m1))

    // When
    execute(DeleteMessageCommand(q1.name, m1.id))

    // Then
    execute(LookupMessageCommand(q1.name, MessageId("xyz"))) must be (None)
  }
}

class MessageStatisticsCommandsTestSuite extends StorageTestSuite {
  val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
  val m1 = createMessageData("xyz", "123", MillisNextDelivery(123L))

  val someTimestamp = 123456789L;

  before {
    execute(CreateQueueCommand(q1))
    execute(new SendMessageCommand(q1.name, m1))
  }

  test("empty statistics should be returned for a non-delivered message") {
    // When
    val stats = execute(GetMessageStatisticsCommand(q1.name, m1.id))

    // Then
    stats.approximateFirstReceive must be (NeverReceived)
    stats.approximateReceiveCount must be (0)
  }

  test("statistics should be correct after receiving a message once") {
    // Given
    execute(UpdateMessageStatisticsCommand(q1.name, m1.id, MessageStatistics(OnDateTimeReceived(new DateTime(someTimestamp)), 1)))

    // When
    val readStats = execute(GetMessageStatisticsCommand(q1.name, m1.id))

    // Then
    readStats.approximateFirstReceive must be (OnDateTimeReceived(new DateTime(someTimestamp)))
    readStats.approximateReceiveCount must be (1)
  }

  test("statistics should be correct after receiving a message twice") {
    // Given
    execute(UpdateMessageStatisticsCommand(q1.name, m1.id, MessageStatistics(OnDateTimeReceived(new DateTime(someTimestamp)), 1)))
    execute(UpdateMessageStatisticsCommand(q1.name, m1.id, MessageStatistics(OnDateTimeReceived(new DateTime(someTimestamp)), 2)))

    // When
    val readStats = execute(GetMessageStatisticsCommand(q1.name, m1.id))

    // Then
    readStats.approximateFirstReceive must be (OnDateTimeReceived(new DateTime(someTimestamp)))
    readStats.approximateReceiveCount must be (2)
  }

  test("statistics should be removed if the message is removed") {
    // Given
    execute(UpdateMessageStatisticsCommand(q1.name, m1.id, MessageStatistics(OnDateTimeReceived(new DateTime(someTimestamp)), 1)))
    execute(DeleteMessageCommand(q1.name, m1.id))

    // When & then
    evaluating { execute(GetMessageStatisticsCommand(q1.name, m1.id)) } must produce [MessageDoesNotExistException]
  }

  test("statistics shouldn't be written if the message is already deleted") {
    // Given
    execute(DeleteMessageCommand(q1.name, m1.id))
    execute(UpdateMessageStatisticsCommand(q1.name, m1.id, MessageStatistics(OnDateTimeReceived(new DateTime(someTimestamp)), 1)))

    // When & then
    evaluating { execute(GetMessageStatisticsCommand(q1.name, m1.id)) } must produce [MessageDoesNotExistException]
  }
}