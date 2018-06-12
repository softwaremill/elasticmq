package org.elasticmq.server.config

import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}

class ElasticMQServerConfigTest extends FunSuite with Matchers {
  test("load the default config") {
    // No exceptions -> good :)
    new ElasticMQServerConfig(ConfigFactory.load("conf/elasticmq"))
  }

  test("load the test config") {
    val conf = new ElasticMQServerConfig(ConfigFactory.load("test"))
    conf.createQueues should have size 3
    conf.createQueues.find(_.deadLettersQueue.isDefined).flatMap(_.deadLettersQueue).map(_.name) should be(
      Some("myDLQ"))
    val fifoQueue = conf.createQueues.find(_.isFifo).get
    fifoQueue.hasContentBasedDeduplication should be(true)
  }
}
