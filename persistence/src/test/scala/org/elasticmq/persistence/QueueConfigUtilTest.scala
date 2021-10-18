package org.elasticmq.persistence

import com.typesafe.config.ConfigFactory
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
    persistedQueues.length shouldBe 1
    persistedQueues.head shouldBe expectedQueue
  }

  test("Persisted queues should take precedence over startup queues with same names") {
    val baseConf = QueueConfigUtil.readPersistedQueuesFromConfig(ConfigFactory.load("test-with-queue-storage-enabled.conf"))
    val queueConf = QueueConfigUtil.readPersistedQueuesFromConfig(ConfigFactory.load("two-queues-config.conf"))
    val loadedQueues = QueueConfigUtil.getQueuesToCreate(queueConf, baseConf)
    val expectedQueue1 = CreateQueueMetadata(
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
    val expectedQueue2 = CreateQueueMetadata(
      "test-2",
      Some(3L),
      Some(0L),
      Some(0L),
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
}
