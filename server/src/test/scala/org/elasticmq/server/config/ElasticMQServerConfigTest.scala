package org.elasticmq.server.config

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ElasticMQServerConfigTest extends AnyFunSuite with Matchers {
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
}
