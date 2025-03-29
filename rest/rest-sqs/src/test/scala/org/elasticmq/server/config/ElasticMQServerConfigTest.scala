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

  test("Should parse base queues") {
    val conf = new ElasticMQServerConfig(ConfigFactory.load("test"))
    conf.baseQueues.size shouldBe 8
  }
}
