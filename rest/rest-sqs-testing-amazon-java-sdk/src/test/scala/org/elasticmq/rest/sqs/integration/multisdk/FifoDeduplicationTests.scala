package org.elasticmq.rest.sqs.integration.multisdk

import org.elasticmq.StringMessageAttribute
import org.elasticmq.rest.sqs.integration.client._
import org.elasticmq.rest.sqs.integration.common.IntegrationTestsBase

trait FifoDeduplicationTests extends IntegrationTestsBase {

  test("FIFO provided message deduplication ids should take priority over content based deduplication") {
    // Given
    val queueUrl = createFifoQueue()

    // When
    testClient.sendMessage(queueUrl, "body", messageDeduplicationId = Some("1"), messageGroupId = Some("1"))
    testClient.sendMessage(queueUrl, "body", messageDeduplicationId = Some("2"), messageGroupId = Some("1"))
    val messages = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(2))

    // Then
    messages should have size 2
  }

  test("Queues should deduplicate messages based on the message body") {
    // Given
    val queueUrl = createFifoQueue()

    // When
    val sendResults = for (i <- 1 to 10) yield {
      testClient.sendMessage(queueUrl, "Message", messageGroupId = Some(s"$i")).toOption.get
    }
    val messages1 = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(4))
    testClient.deleteMessage(queueUrl, messages1.head.receiptHandle)
    val messages2 = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(4))

    // Then
    sendResults.map(_.messageId).toSet should have size 1
    messages1.map(_.body) should have size 1
    messages1.map(_.body).toSet should be(Set("Message"))
    messages2 should have size 0
  }

  test("Queues should deduplicate messages based on the message deduplication attribute") {
    val queueUrl = createFifoQueue()

    // When
    for (i <- 1 to 10) {
      testClient.sendMessage(
        queueUrl,
        s"Message $i",
        messageDeduplicationId = Some("DedupId"),
        messageGroupId = Some("1")
      )
    }

    val m1 = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(1)).headOption
    testClient.deleteMessage(queueUrl, m1.get.receiptHandle)
    val m2 = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(1)).headOption

    // Then
    m1.map(_.body) should be(Some("Message 1"))
    m2 should be(empty)
  }

  test(
    "MD5 of body and attributes of messages should be calculated independently even if they have same deduplication ID"
  ) {
    def createMsgAttribute(value: String) = {
      Map("attribute" -> StringMessageAttribute(value))
    }

    val queueUrl = createFifoQueue()

    val msg1 = testClient
      .sendMessage(
        queueUrl,
        s"Message 1",
        messageDeduplicationId = Some("DedupId"),
        messageGroupId = Some("1"),
        messageAttributes = createMsgAttribute("value1")
      )
      .toOption
      .get
    val msg2 = testClient
      .sendMessage(
        queueUrl,
        s"Message 2",
        messageDeduplicationId = Some("DedupId"),
        messageGroupId = Some("1"),
        messageAttributes = createMsgAttribute("value2")
      )
      .toOption
      .get
    val msg3 = testClient
      .sendMessage(
        queueUrl,
        s"Message 3",
        messageDeduplicationId = Some("DedupId"),
        messageGroupId = Some("1"),
        messageAttributes = createMsgAttribute("value3")
      )
      .toOption
      .get

    msg1.md5OfMessageBody shouldNot be(msg2.md5OfMessageBody)
    msg2.md5OfMessageBody shouldNot be(msg3.md5OfMessageBody)

    msg1.md5OfMessageAttributes shouldNot be(msg2.md5OfMessageAttributes)
    msg2.md5OfMessageAttributes shouldNot be(msg3.md5OfMessageAttributes)

    msg1.messageId shouldBe msg2.messageId
    msg2.messageId shouldBe msg3.messageId
  }

  test(
    "Message with deduplication ID was moved to DLQ and second message with same deduplication ID is sent to SQS, response is successful but receiveMessage returns empty array"
  ) {
    val (queueUrl, _) = createFifoQueueWithDLQ(1)

    val firstSendMessageResponse = testClient
      .sendMessage(queueUrl, s"Message 1", messageDeduplicationId = Some("DedupId"), messageGroupId = Some("1"))
      .toOption
      .get
    val m1 = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(1)).headOption
    val m2 = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(1)).headOption

    m1 should be(defined)
    m2 should not be defined

    val sendMessageWithSameDedupIdResponse = testClient
      .sendMessage(queueUrl, s"Message 3", messageDeduplicationId = Some("DedupId"), messageGroupId = Some("1"))
      .toOption
      .get

    val m3 = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(1)).headOption
    m3 should not be defined

    firstSendMessageResponse.messageId shouldBe sendMessageWithSameDedupIdResponse.messageId
  }

  test(
    "Message with deduplication ID is moved to DLQ (max receive count exceeded) and in DLQ its deduplication ID is changed to Message ID"
  ) {
    val (queueUrl, dlqUrl) = createFifoQueueWithDLQ(2)

    val firstSendMessageResponse = testClient
      .sendMessage(queueUrl, "Message 1", messageDeduplicationId = Some("DedupId"), messageGroupId = Some("1"))
      .toOption
      .get

    testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(1))
    testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(1))
    testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(1))

    val dlqMessage = testClient.receiveMessage(dlqUrl, maxNumberOfMessages = Some(1), systemAttributes = List("All")).head
    dlqMessage.body shouldBe "Message 1"
    dlqMessage.messageId shouldBe firstSendMessageResponse.messageId
    dlqMessage.attributes(MessageDeduplicationId) shouldBe firstSendMessageResponse.messageId
  }

  test(
    "Message is moved to DLQ. Another message with same deduplication ID as the first one had is sent directly do DLQ and both of them are available"
  ) {
    val (queueUrl, dlqUrl) = createFifoQueueWithDLQ(2)

    testClient.sendMessage(queueUrl, "Message 1", messageDeduplicationId = Some("DedupId"), messageGroupId = Some("1"))

    testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(1))
    testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(1))
    testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(1))

    testClient.sendMessage(dlqUrl, "Message 3", messageDeduplicationId = Some("DedupId"), messageGroupId = Some("1"))

    val dlqMessages = testClient.receiveMessage(dlqUrl, systemAttributes = List("All"), maxNumberOfMessages = Some(10))
    dlqMessages should have size 2

    val firstDlqMessage = dlqMessages.find(_.body == "Message 1").get
    firstDlqMessage.attributes(MessageDeduplicationId) shouldBe firstDlqMessage.messageId
    val secondDlqMessage = dlqMessages.find(_.body == "Message 3").get
    secondDlqMessage.attributes(MessageDeduplicationId) shouldBe "DedupId"
  }

  private def createFifoQueue(suffix: Int = 1, attributes: Map[QueueAttributeName, String] = Map.empty): String = {
    testClient
      .createQueue(
        s"testFifoQueue$suffix.fifo",
        attributes ++ Map(
          FifoQueueAttributeName -> "true",
          ContentBasedDeduplicationAttributeName -> "true"
        )
      )
      .toOption
      .get
  }

  private def createFifoQueueWithDLQ(maxReceiveCount: Int): (String, String) = {
    val dlqUrl = testClient
      .createQueue(
        "dlq.fifo",
        Map(
          FifoQueueAttributeName -> "true",
          ContentBasedDeduplicationAttributeName -> "true",
          VisibilityTimeoutAttributeName -> "0"
        )
      )
      .toOption
      .get

    val redrivePolicy =
      s"""{"deadLetterTargetArn":"arn:aws:sqs:elasticmq:123456789012:dlq.fifo","maxReceiveCount":$maxReceiveCount}"""
    val queueUrl =
      testClient
        .createQueue(
          "queue.fifo",
          Map(
            RedrivePolicyAttributeName -> redrivePolicy,
            FifoQueueAttributeName -> "true",
            ContentBasedDeduplicationAttributeName -> "true",
            VisibilityTimeoutAttributeName -> "0"
          )
        )
        .toOption
        .get
    (queueUrl, dlqUrl)
  }
}
