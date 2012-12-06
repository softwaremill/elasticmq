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

  test("receipt handle should be filled when receiving") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))

    execute(CreateQueueCommand(q1))

    execute(new SendMessageCommand(q1.name, createMessageData("xyz", "123", MillisNextDelivery(123L))))

    // When
    val lookupBeforeReceiving = execute(LookupMessageCommand(q1.name, MessageId("xyz")))
    val received = execute(ReceiveMessageCommand(q1.name, 200L, MillisNextDelivery(567L)))
    val lookupAfterReceiving = execute(LookupMessageCommand(q1.name, MessageId("xyz")))

    // Then
    lookupBeforeReceiving.flatMap(_.deliveryReceipt) must be (None)

    val receivedReceipt = received.flatMap(_.deliveryReceipt)
    val lookedUpReceipt = lookupAfterReceiving.flatMap(_.deliveryReceipt)

    receivedReceipt must be ('defined)
    lookedUpReceipt must be ('defined)

    receivedReceipt must be (lookedUpReceipt)
  }

  test("receipt handle should change on subsequent receives") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    execute(CreateQueueCommand(q1))
    execute(new SendMessageCommand(q1.name, createMessageData("xyz", "123", MillisNextDelivery(100L))))

    // When
    val received1 = execute(ReceiveMessageCommand(q1.name, 200L, MillisNextDelivery(300L)))
    val received2 = execute(ReceiveMessageCommand(q1.name, 400L, MillisNextDelivery(500L)))

    // Then
    val received1Receipt = received1.flatMap(_.deliveryReceipt)
    val received2Receipt = received2.flatMap(_.deliveryReceipt)

    received1Receipt must be ('defined)
    received2Receipt must be ('defined)

    received1Receipt must not be (received2Receipt)
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
