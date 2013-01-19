package org.elasticmq.server

import org.scalatest.FunSuite
import org.scalatest.matchers.MustMatchers

class ElasticMQServerConfigTest extends FunSuite with MustMatchers {
  test("load the default config") {
    ElasticMQServerConfig.load
  }
}
