package org.elasticmq.server.config

import com.typesafe.config.ConfigFactory
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
    conf.createQueues should have size 8
    conf.createQueues.find(_.deadLettersQueue.isDefined).flatMap(_.deadLettersQueue).map(_.name) should be(
      Some("myDLQ")
    )
    conf.createQueues.find(_.copyMessagesTo.isDefined).flatMap(_.copyMessagesTo) should be(Some("auditQueue"))
    conf.createQueues.find(_.moveMessagesTo.isDefined).flatMap(_.moveMessagesTo) should be(Some("redirectToQueue"))
    val fifoQueue = conf.createQueues.find(_.isFifo).get
    fifoQueue.hasContentBasedDeduplication should be(true)
    val taggedQueue = conf.createQueues.find(_.tags.nonEmpty).get
    taggedQueue.tags should contain key "tag1"
    taggedQueue.tags should contain value "tagged1"
    taggedQueue.tags should contain key "tag2"
    taggedQueue.tags should contain value "tagged2"
    conf.awsAccountId should be("1111111")
    conf.awsRegion should be("elastic")
  }

  test("FIFO queue should have appended .fifo suffix") {
    val conf = new ElasticMQServerConfig(ConfigFactory.load("test"))
    val fifoQueue = conf.createQueues.find(_.isFifo).value
    fifoQueue.name shouldBe "fifoQueue.fifo"
  }

  test("Fail to load config if FIFO queue name after adding .fifo suffix exceeds 80 characters limit cap") {
    val config = ConfigFactory.load("test")
    val corruptedConfig = config.withValue("queues.exceedsMaximumLimitexceedsMaximumLimitexceedsMaximumLimitexceedsMaximumLimit", config.getObject("queues.fifoQueue"))
    val ex = the[IllegalArgumentException] thrownBy new ElasticMQServerConfig(corruptedConfig)
    ex.getMessage shouldBe "Queue name exceedsMaximumLimitexceedsMaximumLimitexceedsMaximumLimitexceedsMaximumLimit.fifo exceeds maximum length of 80 characters"
  }

  test("Fail to load config if normal queue name exceeds 80 characters limit cap") {
    val config = ConfigFactory.load("test")
    val corruptedConfig = config.withValue("queues.queueName1queueName1queueName1queueName1queueName1queueName1queueName1queueName11", config.getObject("queues.queueName1"))
    val ex = the[IllegalArgumentException] thrownBy new ElasticMQServerConfig(corruptedConfig)
    ex.getMessage shouldBe "Queue name queueName1queueName1queueName1queueName1queueName1queueName1queueName1queueName11 exceeds maximum length of 80 characters"
  }
}
