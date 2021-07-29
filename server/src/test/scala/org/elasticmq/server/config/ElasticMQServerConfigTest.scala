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
    conf.createBaseQueues should have size 8
    conf.createBaseQueues.find(_.deadLettersQueue.isDefined).flatMap(_.deadLettersQueue).map(_.name) should be(
      Some("myDLQ")
    )
    conf.createBaseQueues.find(_.copyMessagesTo.isDefined).flatMap(_.copyMessagesTo) should be(Some("auditQueue"))
    conf.createBaseQueues.find(_.moveMessagesTo.isDefined).flatMap(_.moveMessagesTo) should be(Some("redirectToQueue"))
    val fifoQueue = conf.createBaseQueues.find(_.isFifo).get
    fifoQueue.hasContentBasedDeduplication should be(true)
    val taggedQueue = conf.createBaseQueues.find(_.tags.nonEmpty).get
    taggedQueue.tags should contain key "tag1"
    taggedQueue.tags should contain value "tagged1"
    taggedQueue.tags should contain key "tag2"
    taggedQueue.tags should contain value "tagged2"
    conf.awsAccountId should be("1111111")
    conf.awsRegion should be("elastic")
  }

  test("Normal queues should not have appended .fifo suffix") {
    val conf = new ElasticMQServerConfig(ConfigFactory.load("test"))
    val normalQueues = conf.createBaseQueues.filter(!_.isFifo)
    normalQueues.foreach(_.name should not endWith ".fifo")
  }

  test("FIFO queue should have appended .fifo suffix") {
    val conf = new ElasticMQServerConfig(ConfigFactory.load("test"))
    val fifoQueue = conf.createBaseQueues.find(_.isFifo).value
    fifoQueue.name shouldBe "fifoQueue.fifo"
  }
}
