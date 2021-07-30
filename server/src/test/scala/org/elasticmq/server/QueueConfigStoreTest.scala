package org.elasticmq.server

import com.typesafe.config.ConfigFactory
import org.elasticmq.server.config.ElasticMQServerConfig
import org.elasticmq.{DeadLettersQueueData, MillisVisibilityTimeout, QueueData}
import org.joda.time.{DateTime, Duration}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class QueueConfigStoreTest extends AnyFunSuite with Matchers {

  test("should parse queue data") {
    val conf = new ElasticMQServerConfig(ConfigFactory.load("test"))
    val tags = Map("tag1Key" -> "tag1Value")
    val queueData = QueueData(
      "test",
      MillisVisibilityTimeout(3000L),
      Duration.ZERO,
      Duration.ZERO,
      DateTime.now(),
      DateTime.now(),
      Some(DeadLettersQueueData("dead", 4)),
      isFifo = false,
      hasContentBasedDeduplication = true,
      Some("copyTo"),
      Some("messageTo"),
      tags
    )
    val queues = List(queueData)
    val actualConfig = QueueConfigStore(conf).prepareQueuesConfig(queues)
    val expectedConfig = load(this.getClass,"backup.conf")
    actualConfig should be(expectedConfig)
  }

}
