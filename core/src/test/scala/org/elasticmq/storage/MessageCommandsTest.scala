package org.elasticmq.storage

import org.joda.time.DateTime
import org.elasticmq.{MillisNextDelivery, MessageId, MillisVisibilityTimeout}

abstract class MessageCommandsTest extends StorageTest {
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
    val lookupResult = execute(ReceiveMessagesCommand(q2.name, 1000L, MillisNextDelivery(234L), 1))

    // Then
    lookupResult must be (Nil)
  }

  test("undelivered message should be found in a non-empty queue") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val q2 = createQueueData("q2", MillisVisibilityTimeout(2L))

    execute(CreateQueueCommand(q1))
    execute(CreateQueueCommand(q2))

    execute(new SendMessageCommand(q1.name, createMessageData("xyz", "123", MillisNextDelivery(123L))))

    // When
    val lookupResult = execute(ReceiveMessagesCommand(q1.name, 200L, MillisNextDelivery(234L), 1))

    // Then
    lookupResult must be (List(createMessageData("xyz", "123", MillisNextDelivery(234L))))
  }

  test("next delivery should be updated after receiving") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))

    execute(CreateQueueCommand(q1))

    execute(new SendMessageCommand(q1.name, createMessageData("xyz", "123", MillisNextDelivery(123L))))

    // When
    execute(ReceiveMessagesCommand(q1.name, 200L, MillisNextDelivery(567L), 1))
    val lookupResult = execute(LookupMessageCommand(q1.name, MessageId("xyz")))

    // Then
    lookupResult must be (Some(createMessageData("xyz", "123", MillisNextDelivery(567L))))
  }

  test("receiveing multiple messages should return as much messages as possible") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))

    execute(CreateQueueCommand(q1))

    execute(new SendMessageCommand(q1.name, createMessageData("xyz1", "1", MillisNextDelivery(123L))))
    execute(new SendMessageCommand(q1.name, createMessageData("xyz2", "2", MillisNextDelivery(123L))))
    execute(new SendMessageCommand(q1.name, createMessageData("xyz3", "3", MillisNextDelivery(123L))))

    // When
    val result = execute(ReceiveMessagesCommand(q1.name, 200L, MillisNextDelivery(567L), 5))

    // Then
    result.map(_.content).toSet must be (Set("1", "2", "3"))
  }

  test("next delivery should be updated after receiving multiple messages") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))

    execute(CreateQueueCommand(q1))

    execute(new SendMessageCommand(q1.name, createMessageData("xyz1", "1", MillisNextDelivery(123L))))
    execute(new SendMessageCommand(q1.name, createMessageData("xyz2", "2", MillisNextDelivery(124L))))

    // When
    execute(ReceiveMessagesCommand(q1.name, 200L, MillisNextDelivery(567L), 2))
    val lookupResult1 = execute(LookupMessageCommand(q1.name, MessageId("xyz1")))
    val lookupResult2 = execute(LookupMessageCommand(q1.name, MessageId("xyz2")))

    // Then
    lookupResult1 must be (Some(createMessageData("xyz1", "1", MillisNextDelivery(567L))))
    lookupResult2 must be (Some(createMessageData("xyz2", "2", MillisNextDelivery(567L))))
  }

  test("delivered message should not be found in a non-empty queue when it is not visible") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))

    execute(CreateQueueCommand(q1))

    execute(new SendMessageCommand(q1.name, createMessageData("xyz", "123", MillisNextDelivery(123L))))

    // When
    val lookupResult = execute(ReceiveMessagesCommand(q1.name, 100L, MillisNextDelivery(234L), 1))

    // Then
    lookupResult must be (Nil)
  }

  test("increasing next delivery of a message") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    execute(CreateQueueCommand(q1))

    val m = createMessageData("xyz", "1234", MillisNextDelivery(123L))
    execute(new SendMessageCommand(q1.name, m))

    // When
    execute(UpdateNextDeliveryCommand(q1.name, m.id, MillisNextDelivery(345L)))

    // Then
    execute(LookupMessageCommand(q1.name, MessageId("xyz"))) must be (Some(createMessageData("xyz", "1234", MillisNextDelivery(345L))))
  }

  test("decreasing next delivery of a message") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    execute(CreateQueueCommand(q1))

    // Initially m2 should be delivered after m1
    val m1 = createMessageData("xyz1", "1234", MillisNextDelivery(100L))
    val m2 = createMessageData("xyz2", "1234", MillisNextDelivery(200L))

    execute(new SendMessageCommand(q1.name, m1))
    execute(new SendMessageCommand(q1.name, m2))

    // When
    execute(UpdateNextDeliveryCommand(q1.name, m2.id, MillisNextDelivery(50L)))

    // Then
    // This should find the first message, as it has the visibility timeout decreased.
    execute(ReceiveMessagesCommand(q1.name, 75L, MillisNextDelivery(100L), 1)).map(_.id) must be (List(m2.id))
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
