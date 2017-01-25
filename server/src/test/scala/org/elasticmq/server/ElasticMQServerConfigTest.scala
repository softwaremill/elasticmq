package org.elasticmq.server

import org.scalatest.FunSuite
import org.scalatest.Matchers
import com.typesafe.config.ConfigFactory

class ElasticMQServerConfigTest extends FunSuite with Matchers {
  test("load the default config") {
    // No exceptions -> good :)
    new ElasticMQServerConfig(ConfigFactory.load("conf/elasticmq"))
  }

  test("load the test config") {
    val conf = new ElasticMQServerConfig(ConfigFactory.load("test"))
    conf.createQueues should have size (2)
    conf.createQueues.find(_.deadLettersQueue.isDefined).flatMap(_.deadLettersQueue).map(_.name) should be (Some("myDLQ"))
  }
}
