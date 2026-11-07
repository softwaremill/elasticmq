package org.elasticmq.persistence.file
import com.typesafe.config.ConfigFactory
import org.elasticmq.persistence.{CreateQueueMetadata, DeadLettersQueue}
import org.elasticmq.{DeadLettersQueueData, MillisVisibilityTimeout, QueueData}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.{Duration, OffsetDateTime}

class QueuePersisterTest extends AnyFunSuite with Matchers {
  test("standard queue is converted to config") {

    val queues = List(
      QueueData(
        name = "queue1",
        defaultVisibilityTimeout = MillisVisibilityTimeout.fromSeconds(10),
        delay = Duration.ofSeconds(8),
        receiveMessageWait = Duration.ofSeconds(3),
        created = OffsetDateTime.now(),
        lastModified = OffsetDateTime.now()
      )
    )
    val actual = QueuePersister.prepareQueuesConfig(queues)

    actual shouldBe
      """queues {
        | "queue1" {content-based-deduplication=false,copy-to="",default-visibility-timeout=10000,delay=8000,fifo=false,move-to="",receive-message-wait=3000,tags{}}
        |}""".stripMargin
  }

  test("fifo queue is converted to config") {
    val queues = List(
      QueueData(
        name = "queue2.fifo",
        defaultVisibilityTimeout = MillisVisibilityTimeout.fromSeconds(10),
        delay = Duration.ofSeconds(8),
        receiveMessageWait = Duration.ofSeconds(3),
        created = OffsetDateTime.now(),
        lastModified = OffsetDateTime.now(),
        isFifo = true
      )
    )
    val actual = QueuePersister.prepareQueuesConfig(queues)

    actual shouldBe
      """queues {
        | "queue2.fifo" {content-based-deduplication=false,copy-to="",default-visibility-timeout=10000,delay=8000,fifo=true,move-to="",receive-message-wait=3000,tags{}}
        |}""".stripMargin
  }

  test("written queues are read back") {
    val queues = List(
      QueueData(
        name = "queue3",
        defaultVisibilityTimeout = MillisVisibilityTimeout.fromSeconds(10),
        delay = Duration.ofSeconds(8),
        receiveMessageWait = Duration.ofSeconds(3),
        created = OffsetDateTime.now(),
        lastModified = OffsetDateTime.now(),
        deadLettersQueue = Some(DeadLettersQueueData("dlq", 5)),
        copyMessagesTo = Some("audit"),
        moveMessagesTo = Some("redirect"),
        tags = Map("team" -> "a", "with.dot" -> "b"),
        messageRetentionPeriod = Some(Duration.ofHours(2)),
        maximumMessageSize = Some(2048)
      ),
      QueueData(
        name = "queue4.fifo",
        defaultVisibilityTimeout = MillisVisibilityTimeout.fromSeconds(30),
        delay = Duration.ZERO,
        receiveMessageWait = Duration.ZERO,
        created = OffsetDateTime.now(),
        lastModified = OffsetDateTime.now(),
        isFifo = true,
        hasContentBasedDeduplication = true
      )
    )

    val config = QueuePersister.prepareQueuesConfig(queues)
    config should include("dead-letters-queue{max-receive-count=5,name=dlq}")
    config should include("message-retention-period=7200000")
    config should include("maximum-message-size=2048")

    val read = QueueConfigUtil.readPersistedQueuesFromConfig(ConfigFactory.parseString(config))
    read.map(_.copy(created = 0L, lastModified = 0L)) should contain theSameElementsAs List(
      CreateQueueMetadata(
        "queue3",
        defaultVisibilityTimeoutSeconds = Some(10L),
        delaySeconds = Some(8L),
        receiveMessageWaitSeconds = Some(3L),
        deadLettersQueue = Some(DeadLettersQueue("dlq", 5)),
        copyMessagesTo = Some("audit"),
        moveMessagesTo = Some("redirect"),
        tags = Map("team" -> "a", "with.dot" -> "b"),
        messageRetentionPeriodSeconds = Some(7200L),
        maximumMessageSize = Some(2048)
      ),
      CreateQueueMetadata(
        "queue4.fifo",
        defaultVisibilityTimeoutSeconds = Some(30L),
        delaySeconds = Some(0L),
        receiveMessageWaitSeconds = Some(0L),
        isFifo = true,
        hasContentBasedDeduplication = true
      )
    )
  }
}
