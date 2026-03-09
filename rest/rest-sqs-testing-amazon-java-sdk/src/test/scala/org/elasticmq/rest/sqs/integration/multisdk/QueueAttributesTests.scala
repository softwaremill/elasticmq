package org.elasticmq.rest.sqs.integration.multisdk

import org.elasticmq.rest.sqs.integration.client._
import org.elasticmq.rest.sqs.integration.common.IntegrationTestsBase

trait QueueAttributesTests extends IntegrationTestsBase {

  test("should create a queue with the default visibility timeout") {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get

    // then
    testClient.getQueueAttributes(queueUrl, VisibilityTimeoutAttributeName)(
      VisibilityTimeoutAttributeName.value
    ) shouldBe "30"
  }

  test("should create a queue with the specified visibility timeout") {
    // given
    val queueUrl = testClient.createQueue("testQueue1", Map(VisibilityTimeoutAttributeName -> "14")).toOption.get

    // when
    val attributes = testClient.getQueueAttributes(queueUrl, VisibilityTimeoutAttributeName)

    // then
    attributes(VisibilityTimeoutAttributeName.value) shouldBe "14"
  }

  test("should set queue visibility timeout") {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get

    // when
    testClient.setQueueAttributes(
      queueUrl,
      Map(VisibilityTimeoutAttributeName -> "10")
    )

    // then
    testClient.getQueueAttributes(queueUrl, VisibilityTimeoutAttributeName)(
      VisibilityTimeoutAttributeName.value
    ) shouldBe "10"
  }

  test("should return queue statistics") {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get
    testClient.sendMessage(queueUrl, "m1")
    testClient.sendMessage(queueUrl, "m2")
    testClient.receiveMessage(queueUrl)

    // when
    val attributes = testClient.getQueueAttributes(
      queueUrl,
      ApproximateNumberOfMessagesAttributeName,
      ApproximateNumberOfMessagesNotVisibleAttributeName,
      ApproximateNumberOfMessagesDelayedAttributeName
    )

    // then
    attributes(ApproximateNumberOfMessagesAttributeName.value).toInt shouldBe 1
    attributes(ApproximateNumberOfMessagesNotVisibleAttributeName.value).toInt shouldBe 1
    attributes(ApproximateNumberOfMessagesDelayedAttributeName.value).toInt shouldBe 0
  }

  test("should create a queue with the specified delay") {
    // given
    val queueUrl = testClient.createQueue("testQueue1", Map(DelaySecondsAttributeName -> "14")).toOption.get

    // when
    val attributes = testClient.getQueueAttributes(queueUrl, DelaySecondsAttributeName)

    // then
    attributes(DelaySecondsAttributeName.value) shouldBe "14"
  }

  test("should create a queue with the specified receive message wait time") {
    // given
    val queueUrl =
      testClient.createQueue("testQueue1", Map(ReceiveMessageWaitTimeSecondsAttributeName -> "14")).toOption.get

    // when
    val attributes = testClient.getQueueAttributes(queueUrl, ReceiveMessageWaitTimeSecondsAttributeName)

    // then
    attributes(ReceiveMessageWaitTimeSecondsAttributeName.value) shouldBe "14"
  }

  test("should return error when creating queue with too long name") {
    // expect
    assertError(testClient.createQueue("x" * 81), InvalidAttributeName, "longer than 80")
  }

  test("should return error when creating queue with invalid characters") {
    // expect
    assertError(testClient.createQueue("queue with spaces"), InvalidAttributeName, "Can only include")
  }
}
