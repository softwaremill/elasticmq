package org.elasticmq.server.config

import com.typesafe.config.ConfigFactory
import org.elasticmq.persistence.{CreateQueueMetadata, DeadLettersQueue}
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

  test("Should read the AWS account id") {
    new ElasticMQServerConfig(ConfigFactory.load("test")).awsAccountId shouldBe "1111111"
    new ElasticMQServerConfig(ConfigFactory.load()).awsAccountId shouldBe "000000000000"
  }

  test("Should read the deprecated aws.accountId key") {
    val conf = ConfigFactory.parseString("aws.accountId = \"2222222\"").withFallback(ConfigFactory.load())
    new ElasticMQServerConfig(conf).awsAccountId shouldBe "2222222"
  }

  test("Should build queue URLs from the incoming request by default") {
    new ElasticMQServerConfig(ConfigFactory.load()).nodeAddress.isWildcard shouldBe true
  }

  test("Should parse the auto-create queues template with both key styles") {
    def template(attributes: String) = new ElasticMQServerConfig(
      ConfigFactory
        .parseString(s"auto-create-queues { enabled = true, template { $attributes } }")
        .withFallback(ConfigFactory.load())
    ).autoCreateQueues.template.copy(created = 0L, lastModified = 0L)

    val expected = CreateQueueMetadata(
      name = "",
      defaultVisibilityTimeoutSeconds = Some(7L),
      deadLettersQueue = Some(DeadLettersQueue("dlq", 2))
    )
    template(
      "default-visibility-timeout = 7 seconds, dead-letters-queue { name = dlq, max-receive-count = 2 }"
    ) shouldBe
      expected
    template("defaultVisibilityTimeout = 7 seconds, deadLettersQueue { name = dlq, maxReceiveCount = 2 }") shouldBe
      expected
  }
}
