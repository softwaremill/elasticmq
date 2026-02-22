package org.elasticmq.rest.sqs.aws

import org.elasticmq.StringMessageAttribute
import org.elasticmq.rest.sqs.client._

trait MessageAttributesTests extends AmazonJavaSdkNewTestBase {

  test("Sending message with empty attribute value and String data type should result in error") {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get

    // expect
    assertError(
      testClient.sendMessage(
        queueUrl,
        "Message 1",
        messageAttributes = Map("attribute1" -> StringMessageAttribute("", Some("String")))
      ),
      InvalidParameterValue,
      "Attribute 'attribute1' must contain a non-empty value of type 'String"
    )
  }

  test("Sending message with empty attribute value and Number data type should result in error") {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get

    // expect
    assertError(
      testClient.sendMessage(
        queueUrl,
        "Message 1",
        messageAttributes = Map("attribute1" -> StringMessageAttribute("", Some("Number")))
      ),
      InvalidParameterValue,
      "must contain a non-empty value"
    )
  }

  test("Sending message with empty attribute type should result in error") {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get

    // expect
    assertError(
      testClient.sendMessage(
        queueUrl,
        "Message 1",
        messageAttributes = Map("attribute1" -> StringMessageAttribute("value", Some("")))
      ),
      InvalidParameterValue,
      "" // Message varies between SDK versions and based on whether it's a client or server error
    )
  }

  test(
    "Sending message in batch should result in accepting only those messages that do not have empty message attributes"
  ) {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get

    // when
    val result = testClient.sendMessageBatch(
      queueUrl,
      List(
        SendMessageBatchEntry(
          "1",
          "Message 1",
          messageAttributes = Map("attribute1" -> StringMessageAttribute("value1"))
        ),
        SendMessageBatchEntry(
          "2",
          "Message 2",
          messageAttributes = Map("attribute1" -> StringMessageAttribute("", Some("String")))
        ),
        SendMessageBatchEntry(
          "3",
          "Message 3",
          messageAttributes = Map("attribute1" -> StringMessageAttribute("value1"))
        )
      )
    ).toOption.get

    // then
    result.successful should have size 2
    result.failed should have size 1

    val messages = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))
    messages should have size 2
    messages.map(_.body).toSet should be(Set("Message 1", "Message 3"))
  }

  test("For normal queues, MessageDeduplicationId and MessageGroupId should not be sent with ReceiveMessage response") {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get
    testClient.sendMessage(queueUrl, "Message 1")

    // when
    val messages = testClient.receiveMessage(queueUrl, systemAttributes = List("All"))

    // then
    messages should have size 1
    val messageAttributes = messages.head.attributes
    messageAttributes.get(MessageDeduplicationId) should not be defined
    messageAttributes.get(MessageGroupId) should not be defined
  }

  test("For FIFO queues, MessageDeduplicationId and MessageGroupId should be sent with ReceiveMessage response") {
    // given
    val queueUrl = testClient
      .createQueue(
        "testQueue1.fifo",
        Map(FifoQueueAttributeName -> "true", ContentBasedDeduplicationAttributeName -> "true")
      )
      .toOption
      .get
    testClient.sendMessage(
      queueUrl,
      "Message 1",
      messageDeduplicationId = Some("123"),
      messageGroupId = Some("456")
    )

    // when
    val messages = testClient.receiveMessage(queueUrl, systemAttributes = List("All"))

    // then
    messages should have size 1
    val messageAttributes = messages.head.attributes
    messageAttributes.get(MessageDeduplicationId) shouldBe Some("123")
    messageAttributes.get(MessageGroupId) shouldBe Some("456")
  }

}
