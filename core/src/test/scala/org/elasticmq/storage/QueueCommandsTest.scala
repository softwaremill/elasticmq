package org.elasticmq.storage

import org.elasticmq._
import org.elasticmq.data.QueueData
import org.joda.time.{Duration, DateTime}

abstract class QueueCommandsTest extends StorageTest {
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
    evaluating {
      execute(CreateQueueCommand(q1))
    } must produce[QueueAlreadyExistsException]
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
    val m3 = createMessageData("m3", "123", MillisNextDelivery(124L));
    execute(SendMessageCommand(queue.name, m3))
    val m4 = createMessageData("m4", "123", MillisNextDelivery(125L));
    execute(SendMessageCommand(queue.name, m4))
    val m5 = createMessageData("m5", "123", MillisNextDelivery(126L));
    execute(SendMessageCommand(queue.name, m5))
    val m6 = createMessageData("m6", "123", MillisNextDelivery(126L));
    execute(SendMessageCommand(queue.name, m6))

    execute(UpdateMessageStatisticsCommand(queue.name, m3.id, MessageStatistics(OnDateTimeReceived(new DateTime(100L)), 1)))

    // Stats are inserted if the counter is 1, updated if it's more than 1. So we first have to insert a row with 1.
    execute(UpdateMessageStatisticsCommand(queue.name, m4.id, MessageStatistics(OnDateTimeReceived(new DateTime(101L)), 1)))
    execute(UpdateMessageStatisticsCommand(queue.name, m4.id, MessageStatistics(OnDateTimeReceived(new DateTime(102L)), 2)))

    execute(UpdateMessageStatisticsCommand(queue.name, m5.id, MessageStatistics(OnDateTimeReceived(new DateTime(102L)), 1)))
    execute(UpdateMessageStatisticsCommand(queue.name, m5.id, MessageStatistics(OnDateTimeReceived(new DateTime(104L)), 3)))

    execute(UpdateMessageStatisticsCommand(queue.name, m6.id, MessageStatistics(OnDateTimeReceived(new DateTime(103L)), 1)))

    // Delayed messages - never yet received
    val m7 = createMessageData("m7", "123", MillisNextDelivery(127L));
    execute(SendMessageCommand(queue.name, m7))
    val m8 = createMessageData("m8", "123", MillisNextDelivery(128L));
    execute(SendMessageCommand(queue.name, m8))
    val m9 = createMessageData("m9", "123", MillisNextDelivery(129L));
    execute(SendMessageCommand(queue.name, m9))

    // When
    val stats = execute(GetQueueStatisticsCommand(queue.name, 123L))

    // Then
    stats must be (QueueStatistics(2L, 4L, 3L))
  }
}
