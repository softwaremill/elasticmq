package org.elasticmq.storage

import org.elasticmq._
import org.joda.time.DateTime

class MessageStatisticsCommandsTest extends MultiStorageTest {
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
    evaluating {
      execute(GetMessageStatisticsCommand(q1.name, m1.id))
    } must produce[MessageDoesNotExistException]
  }

  test("statistics shouldn't be written if the message is already deleted") {
    // Given
    execute(DeleteMessageCommand(q1.name, m1.id))
    execute(UpdateMessageStatisticsCommand(q1.name, m1.id, MessageStatistics(OnDateTimeReceived(new DateTime(someTimestamp)), 1)))

    // When & then
    evaluating {
      execute(GetMessageStatisticsCommand(q1.name, m1.id))
    } must produce[MessageDoesNotExistException]
  }
}
