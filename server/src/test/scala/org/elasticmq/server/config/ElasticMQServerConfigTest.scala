package org.elasticmq.server.config

import com.typesafe.config.ConfigFactory
import org.elasticmq.server
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ElasticMQServerConfigTest extends AnyFunSuite with Matchers with OptionValues {
  test("load the default config") {
    // No exceptions -> good :)
    new ElasticMQServerConfig(ConfigFactory.load("conf/elasticmq"))
  }

  test("load the test config") {
    val conf = new ElasticMQServerConfig(ConfigFactory.load("test"))
    conf.readQueuesToLoad() should have size 8
    conf.readQueuesToLoad().find(_.deadLettersQueue.isDefined).flatMap(_.deadLettersQueue).map(_.name) should be(
      Some("myDLQ")
    )
    conf.readQueuesToLoad().find(_.copyMessagesTo.isDefined).flatMap(_.copyMessagesTo) should be(Some("auditQueue"))
    conf.readQueuesToLoad().find(_.moveMessagesTo.isDefined).flatMap(_.moveMessagesTo) should be(Some("redirectToQueue"))
    val fifoQueue = conf.readQueuesToLoad().find(_.isFifo).get
    fifoQueue.hasContentBasedDeduplication should be(true)
    val taggedQueue = conf.readQueuesToLoad().find(_.tags.nonEmpty).get
    taggedQueue.tags should contain key "tag1"
    taggedQueue.tags should contain value "tagged1"
    taggedQueue.tags should contain key "tag2"
    taggedQueue.tags should contain value "tagged2"
    conf.awsAccountId should be("1111111")
    conf.awsRegion should be("elastic")
  }

  test("Normal queues should not have appended .fifo suffix") {
    val conf = new ElasticMQServerConfig(ConfigFactory.load("test"))
    val normalQueues = conf.readQueuesToLoad().filter(!_.isFifo)
    normalQueues.foreach(_.name should not endWith ".fifo")
  }

  test("FIFO queue should have appended .fifo suffix") {
    val conf = new ElasticMQServerConfig(ConfigFactory.load("test"))
    val fifoQueue = conf.readQueuesToLoad().find(_.isFifo).value
    fifoQueue.name shouldBe "fifoQueue.fifo"
  }

  test("Should correctly parse persisted queues configuration") {
    val conf = new ElasticMQServerConfig(ConfigFactory.load("test"))
    val config = ConfigFactory.parseString(server.load(this.getClass, "expected-single-queue-config.conf"))
    val persistedQueues = conf.readPersistedQueues(Some(config))
    val expectedQueue = CreateQueue(
      "test",
      Some(3L),
      Some(0L),
      Some(0L),
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

  test("Should not parse persisted queues when disabled") {
    val conf = new ElasticMQServerConfig(ConfigFactory.load("test"))
    val persistedQueues = conf.readPersistedQueues()
    persistedQueues shouldBe Nil
  }

  test("Persisted queues should take precedence over startup queues with same names") {
    val conf = new ElasticMQServerConfig(ConfigFactory.load("test-with-queue-storage-enabled.conf"))
    val config = ConfigFactory.parseString(server.load(this.getClass, "two-queues-config.conf"))
    val persistedQueues = conf.readPersistedQueues(Some(config))
    val loadedQueues = conf.readQueuesToLoad(persistedQueues)
    val expectedQueue = CreateQueue("test", Some(3L), Some(0L), Some(0L), Some(DeadLettersQueue("dead", 4)), isFifo = false,
      hasContentBasedDeduplication = true, Some("copyTo"), Some("messageTo"), Map("tag1Key" -> "tag1Value"))
    val expectedQueue1 = CreateQueue("test-2", Some(3L), Some(0L), Some(0L), Some(DeadLettersQueue("dead-2", 4)), isFifo = false,
      hasContentBasedDeduplication = true, Some("copyTo2"), Some("messageTo2"), Map("tag12Key" -> "tag12Value"))
    val expectedQueue2 = CreateQueue("test-3", Some(3L), Some(0L), Some(0L), Some(DeadLettersQueue("dead-3", 4)), isFifo = false,
      hasContentBasedDeduplication = true, Some("copyTo3"), Some("messageTo3"), Map("tag13Key" -> "tag13Value"))
    loadedQueues.length shouldBe 3
    loadedQueues.head shouldBe expectedQueue
    loadedQueues(1) shouldBe expectedQueue1
    loadedQueues(2) shouldBe expectedQueue2
  }
}
