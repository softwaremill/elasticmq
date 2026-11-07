package org.elasticmq.persistence.file

import com.typesafe.config.ConfigFactory
import org.elasticmq.persistence.{CreateQueueMetadata, DeadLettersQueue}
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class QueueConfigUtilTest extends AnyFunSuite with Matchers with OptionValues {

  test("load the test config") {
    val conf = ConfigFactory.load("test")
    val queuesToCreate = QueueConfigUtil.readPersistedQueuesFromConfig(conf)

    queuesToCreate should have size 8
    queuesToCreate.find(_.deadLettersQueue.isDefined).flatMap(_.deadLettersQueue).map(_.name) should be(
      Some("myDLQ")
    )
    queuesToCreate.find(_.copyMessagesTo.isDefined).flatMap(_.copyMessagesTo) should be(Some("auditQueue"))
    queuesToCreate.find(_.moveMessagesTo.isDefined).flatMap(_.moveMessagesTo) should be(
      Some("redirectToQueue")
    )
    val fifoQueue = queuesToCreate.find(_.isFifo).get
    fifoQueue.hasContentBasedDeduplication should be(true)
    val taggedQueue = queuesToCreate.find(_.tags.nonEmpty).get
    taggedQueue.tags should contain key "tag1"
    taggedQueue.tags should contain value "tagged1"
    taggedQueue.tags should contain key "tag2"
    taggedQueue.tags should contain value "tagged2"
  }

  test("Normal queues should not have appended .fifo suffix") {
    val conf = ConfigFactory.load("test")
    val queuesToCreate = QueueConfigUtil.readPersistedQueuesFromConfig(conf)
    val normalQueues = queuesToCreate.filter(!_.isFifo)
    normalQueues.foreach(_.name should not endWith ".fifo")
  }

  test("FIFO queue should have appended .fifo suffix") {
    val conf = ConfigFactory.load("test")
    val queuesToCreate = QueueConfigUtil.readPersistedQueuesFromConfig(conf)
    val fifoQueue = queuesToCreate.find(_.isFifo).value
    fifoQueue.name shouldBe "fifoQueue.fifo"
  }

  test("Should correctly parse persisted queues configuration") {
    val conf = ConfigFactory.load("expected-single-queue-config.conf")
    val persistedQueues = QueueConfigUtil.readPersistedQueuesFromConfig(conf)
    val expectedQueue = CreateQueueMetadata(
      "test",
      Some(3L),
      Some(0L),
      Some(0L),
      100L,
      200L,
      Some(DeadLettersQueue("dead", 4)),
      isFifo = false,
      hasContentBasedDeduplication = true,
      Some("copyTo"),
      Some("messageTo"),
      Map("tag1Key" -> "tag1Value")
    )
    val expectedDeadLetters = CreateQueueMetadata("dead")
    persistedQueues.length shouldBe 2
    persistedQueues.head shouldBe expectedDeadLetters
    persistedQueues.last shouldBe expectedQueue
  }

  test("Persisted queues should take precedence over startup queues with same names") {
    val baseConf =
      QueueConfigUtil.readPersistedQueuesFromConfig(ConfigFactory.load("test-with-queue-storage-enabled.conf"))
    val queueConf = QueueConfigUtil.readPersistedQueuesFromConfig(ConfigFactory.load("two-queues-config.conf"))
    val loadedQueues = CreateQueueMetadata.mergePersistedAndBaseQueues(queueConf, baseConf)
    val expectedQueue1 = CreateQueueMetadata(
      "test",
      Some(5L),
      Some(2L),
      Some(20L),
      100L,
      200L,
      Some(DeadLettersQueue("dead", 4)),
      isFifo = false,
      hasContentBasedDeduplication = true,
      Some("copyTo"),
      Some("messageTo"),
      Map("tag1Key" -> "tag1Value")
    )
    val expectedQueue2 = CreateQueueMetadata(
      "test-2",
      Some(3L),
      Some(1L),
      Some(25L),
      100L,
      200L,
      Some(DeadLettersQueue("dead-2", 4)),
      isFifo = false,
      hasContentBasedDeduplication = true,
      Some("copyTo2"),
      Some("messageTo2"),
      Map("tag12Key" -> "tag12Value")
    )
    val expectedQueue3 = CreateQueueMetadata(
      "test-3",
      Some(3L),
      Some(0L),
      Some(0L),
      100L,
      200L,
      Some(DeadLettersQueue("dead-3", 4)),
      isFifo = false,
      hasContentBasedDeduplication = true,
      Some("copyTo3"),
      Some("messageTo3"),
      Map("tag13Key" -> "tag13Value")
    )
    loadedQueues.length shouldBe 3

    val loadedQueuesMap = loadedQueues.map(e => e.name -> e).toMap

    loadedQueuesMap("test") shouldBe expectedQueue1
    loadedQueuesMap("test-2") shouldBe expectedQueue2
    loadedQueuesMap("test-3") shouldBe expectedQueue3
  }

  test("Should read queue attributes written with kebab-case keys") {
    val conf = ConfigFactory.parseString("""
      |queues {
      |  q {
      |    default-visibility-timeout = 3 seconds
      |    delay = 1 second
      |    receive-message-wait = 2 seconds
      |    created = 100
      |    last-modified = 200
      |    dead-letters-queue { name = dead, max-receive-count = 4 }
      |    content-based-deduplication = true
      |    fifo = true
      |    copy-to = audit
      |    move-to = redirect
      |    tags { tag1 = value1 }
      |    message-retention-period = 2 hours
      |    maximum-message-size = 256K
      |  }
      |}""".stripMargin)

    QueueConfigUtil.readPersistedQueuesFromConfig(conf) shouldBe List(
      CreateQueueMetadata(
        "q.fifo",
        Some(3L),
        Some(1L),
        Some(2L),
        100L,
        200L,
        Some(DeadLettersQueue("dead", 4)),
        isFifo = true,
        hasContentBasedDeduplication = true,
        Some("audit"),
        Some("redirect"),
        Map("tag1" -> "value1"),
        messageRetentionPeriodSeconds = Some(7200L),
        maximumMessageSize = Some(262144)
      )
    )
  }

  test("Deprecated camelCase keys take precedence over kebab-case keys, and can be mixed with them") {
    val conf = ConfigFactory.parseString("""
      |queues {
      |  q {
      |    defaultVisibilityTimeout = 3 seconds
      |    default-visibility-timeout = 5 seconds
      |    deadLettersQueue { name = dead, max-receive-count = 4 }
      |    copy-to = audit
      |  }
      |}""".stripMargin)

    val queue = QueueConfigUtil.readPersistedQueuesFromConfig(conf).head
    queue.defaultVisibilityTimeoutSeconds shouldBe Some(3L)
    queue.deadLettersQueue shouldBe Some(DeadLettersQueue("dead", 4))
    queue.copyMessagesTo shouldBe Some("audit")
  }

  test("Should read queue attributes for a template") {
    val template = QueueConfigUtil.readQueueAttributes(
      ConfigFactory.parseString("default-visibility-timeout = 30 seconds, fifo = true, tags { type = dynamic }")
    )
    template.name shouldBe ""
    template.defaultVisibilityTimeoutSeconds shouldBe Some(30L)
    template.isFifo shouldBe true
    template.tags shouldBe Map("type" -> "dynamic")
  }

  test("Queues without a retention period or maximum message size leave them unset") {
    val queue = QueueConfigUtil.readQueueAttributes(ConfigFactory.parseString("delay = 1 second"))
    queue.messageRetentionPeriodSeconds shouldBe None
    queue.maximumMessageSize shouldBe None
  }

  test("A non-positive maximum message size is rejected") {
    an[IllegalArgumentException] should be thrownBy QueueConfigUtil.readQueueAttributes(
      ConfigFactory.parseString("maximum-message-size = 0")
    )
  }
}
