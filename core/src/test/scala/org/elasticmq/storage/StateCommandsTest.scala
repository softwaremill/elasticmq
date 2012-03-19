package org.elasticmq.storage

import org.elasticmq._
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import org.joda.time.DateTime

class StateCommandsTest extends MultiStorageTest {
  test("dumping and restoring empty state") {
    dumpAndRestoreState()
  }

  test("dumping and restoring single queue") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    execute(CreateQueueCommand(q1))

    // When
    dumpAndRestoreState()

    // Then
    execute(LookupQueueCommand(q1.name)) must be (Some(q1))
  }

  test("dumping and restoring multiple queues") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val q2 = createQueueData("q2", MillisVisibilityTimeout(2L))
    val q3 = createQueueData("q3", MillisVisibilityTimeout(3L))

    execute(CreateQueueCommand(q1))
    execute(CreateQueueCommand(q2))
    execute(CreateQueueCommand(q3))

    // When
    dumpAndRestoreState()

    // Then
    execute(LookupQueueCommand(q1.name)) must be (Some(q1))
    execute(LookupQueueCommand(q2.name)) must be (Some(q2))
    execute(LookupQueueCommand(q3.name)) must be (Some(q3))
  }

  test("dumping and restoring single message") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val m1 = createMessageData("m1", "c1", MillisNextDelivery(100L))

    execute(CreateQueueCommand(q1))
    execute(SendMessageCommand(q1.name, m1))

    // When
    dumpAndRestoreState()

    // Then
    execute(LookupMessageCommand(q1.name, m1.id)) must be (Some(m1))
  }

  test("dumping and restoring multiple message") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val q2 = createQueueData("q2", MillisVisibilityTimeout(2L))

    val m1 = createMessageData("m1", "c1", MillisNextDelivery(100L))
    val m2 = createMessageData("m2", "c2", MillisNextDelivery(101L))
    val m3 = createMessageData("m3", "c3", MillisNextDelivery(102L))

    execute(CreateQueueCommand(q1))
    execute(CreateQueueCommand(q2))

    execute(SendMessageCommand(q1.name, m1))
    execute(SendMessageCommand(q2.name, m2))
    execute(SendMessageCommand(q2.name, m3))

    // When
    dumpAndRestoreState()

    // Then
    execute(LookupMessageCommand(q1.name, m1.id)) must be (Some(m1))
    execute(LookupMessageCommand(q2.name, m2.id)) must be (Some(m2))
    execute(LookupMessageCommand(q2.name, m3.id)) must be (Some(m3))
  }

  test("dumping and restoring statistics") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))

    val m1 = createMessageData("m1", "c1", MillisNextDelivery(1L))
    val m2 = createMessageData("m2", "c2", MillisNextDelivery(10001L))
    val m3 = createMessageData("m3", "c3", MillisNextDelivery(20001L))

    val s1 = MessageStatistics(OnDateTimeReceived(new DateTime(0)), 2)
    val s2 = MessageStatistics(OnDateTimeReceived(new DateTime(10000)), 0)
    val s3 = MessageStatistics(OnDateTimeReceived(new DateTime(20000)), 3)

    execute(CreateQueueCommand(q1))

    execute(SendMessageCommand(q1.name, m1))
    execute(SendMessageCommand(q1.name, m2))
    execute(SendMessageCommand(q1.name, m3))

    execute(UpdateMessageStatisticsCommand(q1.name, m1.id, s1))
    execute(UpdateMessageStatisticsCommand(q1.name, m2.id, s2))
    execute(UpdateMessageStatisticsCommand(q1.name, m3.id, s3))

    // When
    dumpAndRestoreState()

    // Then
    execute(GetMessageStatisticsCommand(q1.name, m1.id)) must be (s1)
    execute(GetMessageStatisticsCommand(q1.name, m2.id)) must be (s2)
    execute(GetMessageStatisticsCommand(q1.name, m3.id)) must be (s3)

    execute(GetQueueStatisticsCommand(q1.name, 5000L)) must be (QueueStatistics(1L, 1L, 1L))
  }
  
  def dumpAndRestoreState() {
    val ouputStream = new ByteArrayOutputStream()
    execute(DumpStateCommand(ouputStream))
    val bytes = ouputStream.toByteArray

    newStorageCommandExecutor()

    execute(RestoreStateCommand(new ByteArrayInputStream(bytes)))
  }
}
