package org.elasticmq.rest.sqs.integration.multisdk

import org.elasticmq.rest.sqs.integration.client._

trait FifoQueueTests extends AmazonJavaMultiSdkTestBase {

  test("FIFO queues should return an error if the queue's name does not end in .fifo") {
    // expect
    assertError(
      testClient.createQueue("testQueue1", Map(FifoQueueAttributeName -> "true")),
      InvalidAttributeName,
      "must end with .fifo suffix"
    )
  }

  test("FIFO queues should return an error if an invalid message group id parameter is provided") {
    // given
    val fifoQueueUrl = testClient
      .createQueue(
        "testQueue.fifo",
        Map(FifoQueueAttributeName -> "true", ContentBasedDeduplicationAttributeName -> "true")
      )
      .toOption
      .get
    val regularQueueUrl = testClient.createQueue("testQueue1").toOption.get

    // expect
    // An illegal character
    assertError(
      testClient.sendMessage(fifoQueueUrl, "A body", messageGroupId = Some("æ")),
      InvalidParameterValue,
      "MessageGroupId"
    )

    // More than 128 characters
    val id = "1" * 129
    assertError(
      testClient.sendMessage(fifoQueueUrl, "A body", messageGroupId = Some(id)),
      InvalidParameterValue,
      "MessageGroupId"
    )

    // Message group IDs are required for fifo queues
    assertError(
      testClient.sendMessage(fifoQueueUrl, "A body"),
      MissingParameter,
      "MessageGroupId"
    )

    // Regular queues now allow message groups
    testClient.sendMessage(regularQueueUrl, "A body", messageGroupId = Some("group-1")).isRight shouldBe true
  }

  test("FIFO queues do not support delaying individual messages") {
    // given
    val queueUrl = testClient
      .createQueue(
        "testQueue.fifo",
        Map(FifoQueueAttributeName -> "true", ContentBasedDeduplicationAttributeName -> "true")
      )
      .toOption
      .get

    // expect
    assertError(
      testClient.sendMessage(
        queueUrl,
        "body",
        delaySeconds = Some(10),
        messageDeduplicationId = Some("1"),
        messageGroupId = Some("1")
      ),
      InvalidParameterValue,
      "DelaySeconds"
    )

    val result = testClient
      .sendMessageBatch(
        queueUrl,
        List(
          SendMessageBatchEntry("1", "Message 1", messageGroupId = Some("1")),
          SendMessageBatchEntry("2", "Message 2", messageGroupId = Some("2"), delaySeconds = Some(10))
        )
      )
      .toOption
      .get
    result.successful should have size 1
    result.failed should have size 1
    result.failed.head.code shouldBe "InvalidParameterValue"

    // Sanity check that a 0 delay seconds value is accepted
    testClient.sendMessage(
      queueUrl,
      "body",
      delaySeconds = Some(0),
      messageDeduplicationId = Some("2"),
      messageGroupId = Some("1")
    ).isRight shouldBe true
  }

  test(
    "FIFO queues should not return a second message for the same message group if the first has not been deleted yet"
  ) {
    // given
    val queueUrl = testClient
      .createQueue(
        "testQueue.fifo",
        Map(FifoQueueAttributeName -> "true", ContentBasedDeduplicationAttributeName -> "true")
      )
      .toOption
      .get

    // when
    val messages1 = testClient.receiveMessage(queueUrl)
    testClient.sendMessage(queueUrl, "Message 1", messageGroupId = Some("group-1"))
    testClient.sendMessage(queueUrl, "Message 2", messageGroupId = Some("group-1"))

    val messages2 = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(1))
    val messages3 = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(1))
    testClient.deleteMessage(queueUrl, messages2.head.receiptHandle)
    val messages4 = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(1))

    // then
    messages1 should have size 0
    messages2.map(_.body).toSet should be(Set("Message 1"))
    messages3 should have size 0
    messages4.map(_.body).toSet should be(Set("Message 2"))
  }

  test("FIFO queues should block messages for the visibility timeout period within a message group") {
    // given
    val queueUrl = testClient
      .createQueue(
        "testQueue.fifo",
        Map(
          FifoQueueAttributeName -> "true",
          ContentBasedDeduplicationAttributeName -> "true",
          VisibilityTimeoutAttributeName -> "1"
        )
      )
      .toOption
      .get

    // when
    testClient.sendMessage(queueUrl, "Message 1", messageGroupId = Some("group"))
    testClient.sendMessage(queueUrl, "Message 2", messageGroupId = Some("group"))

    val m1 = testClient.receiveMessage(queueUrl).headOption
    val m2 = testClient.receiveMessage(queueUrl).headOption
    Thread.sleep(1100)
    val m3 = testClient.receiveMessage(queueUrl).headOption

    // then
    m1.map(_.body) should be(Some("Message 1"))
    m2 should be(empty)
    m3.map(_.body) should be(Some("Message 1"))
  }

  test("should receive system all attributes") {
    // given
    val queueUrl = testClient
      .createQueue(
        "testQueue2.fifo",
        Map(FifoQueueAttributeName -> "true", ContentBasedDeduplicationAttributeName -> "false")
      )
      .toOption
      .get
    testClient.sendMessage(
      queueUrl,
      "test123",
      messageGroupId = Option("gp1"),
      messageDeduplicationId = Option("dup1")
    )

    // when
    val messages = testClient.receiveMessage(queueUrl, systemAttributes = List("All"))

    // then
    messages should have size 1
    val messageAttributes = messages.head.attributes
    messageAttributes(MessageDeduplicationId) shouldBe "dup1"
    messageAttributes(MessageGroupId) shouldBe "gp1"
  }

  test("should ignore zero delay seconds on message level with fifo queue") {
    // given
    val queueUrl =
      testClient
        .createQueue("testQueue1.fifo", Map(FifoQueueAttributeName -> "true", DelaySecondsAttributeName -> "5"))
        .toOption
        .get
    testClient.sendMessage(
      queueUrl,
      "test123",
      delaySeconds = Some(0),
      messageGroupId = Option("group1"),
      messageDeduplicationId = Option("dedup1")
    )

    // when
    val messages = testClient.receiveMessage(queueUrl)

    // then
    messages shouldBe empty
  }
}
