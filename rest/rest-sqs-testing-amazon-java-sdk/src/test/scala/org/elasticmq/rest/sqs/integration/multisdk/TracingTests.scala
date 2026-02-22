package org.elasticmq.rest.sqs.integration.multisdk

import org.elasticmq.rest.sqs.integration.client.{AWSTraceHeader, SendMessageBatchEntry}
import org.elasticmq.rest.sqs.integration.common.IntegrationTestsBase

trait TracingTests extends IntegrationTestsBase {

  test("Message should not have assigned trace if such one was not provided") {
    val queueUrl = testClient.createQueue("testQueue1").toOption.get

    testClient.sendMessage(queueUrl, "Message 1")

    val message = testClient
      .receiveMessage(queueUrl, systemAttributes = List("All"), maxNumberOfMessages = Some(1))
      .head
    message.attributes.get(AWSTraceHeader) shouldBe None
  }

  test("Trace ID should be returned if it was assigned to message (via message system attribute)") {
    val queueUrl = testClient.createQueue("testQueue1").toOption.get

    testClient.sendMessage(queueUrl, "Message 1", awsTraceHeader = Some("123456789"))

    val message = testClient
      .receiveMessage(queueUrl, systemAttributes = List("All"), maxNumberOfMessages = Some(1))
      .head
    message.attributes.get(AWSTraceHeader) shouldBe Some("123456789")
  }

  test("Trace ID should be returned if it was assigned to message (via sendMessage request header)") {
    val queueUrl = testClient.createQueue("testQueue1").toOption.get

    testClient.sendMessage(queueUrl, "Message 1", customHeaders = Map("X-Amzn-Trace-Id" -> "987654321"))

    val message = testClient
      .receiveMessage(queueUrl, systemAttributes = List("All"), maxNumberOfMessages = Some(1))
      .head
    message.attributes.get(AWSTraceHeader) shouldBe Some("987654321")
  }

  test(
    "Sending several messages in batch request and applying trace ID in system attributes per message should result in different traces assigned to different messages"
  ) {
    val queueUrl = testClient.createQueue("testQueue1").toOption.get

    testClient.sendMessageBatch(
      queueUrl,
      List(
        SendMessageBatchEntry("id1", "message 1", awsTraceHeader = Some("1")),
        SendMessageBatchEntry("id2", "message 2", awsTraceHeader = Some("2"))
      )
    )

    val messages = testClient.receiveMessage(queueUrl, systemAttributes = List("All"), maxNumberOfMessages = Some(2))

    messages.find(_.body == "message 1").get.attributes.get(AWSTraceHeader) shouldBe Some("1")
    messages.find(_.body == "message 2").get.attributes.get(AWSTraceHeader) shouldBe Some("2")
  }

  test(
    "Sending several messages in batch request and applying trace ID as a header to request, should result in assigning same trace ID to all messages in batch"
  ) {
    val queueUrl = testClient.createQueue("testQueue1").toOption.get

    testClient.sendMessageBatch(
      queueUrl,
      List(
        SendMessageBatchEntry("id1", "message 1"),
        SendMessageBatchEntry("id2", "message 2")
      ),
      customHeaders = Map("X-Amzn-Trace-Id" -> "987654321")
    )

    val messages = testClient.receiveMessage(queueUrl, systemAttributes = List("All"), maxNumberOfMessages = Some(2))

    messages.find(_.body == "message 1").get.attributes.get(AWSTraceHeader) shouldBe Some("987654321")
    messages.find(_.body == "message 2").get.attributes.get(AWSTraceHeader) shouldBe Some("987654321")
  }

  test("Trace ID provided as a system attribute should have precedence over trace ID provided in request header") {
    val queueUrl = testClient.createQueue("testQueue1").toOption.get

    testClient.sendMessage(
      queueUrl,
      "Message 1",
      awsTraceHeader = Some("123456789"),
      customHeaders = Map("X-Amzn-Trace-Id" -> "987654321")
    )

    val message = testClient
      .receiveMessage(queueUrl, systemAttributes = List("All"), maxNumberOfMessages = Some(1))
      .head
    message.attributes.get(AWSTraceHeader) shouldBe Some("123456789")
  }

  test("Client should be able to ask for Trace ID attribute") {
    val queueUrl = testClient.createQueue("testQueue1").toOption.get

    testClient.sendMessage(
      queueUrl,
      "Message 1",
      awsTraceHeader = Some("123456789"),
      customHeaders = Map("X-Amzn-Trace-Id" -> "987654321")
    )

    val message = testClient
      .receiveMessage(queueUrl, systemAttributes = List("AWSTraceHeader"), maxNumberOfMessages = Some(1))
      .head

    message.attributes should have size 1
    message.attributes.get(AWSTraceHeader) shouldBe Some("123456789")
  }
}
