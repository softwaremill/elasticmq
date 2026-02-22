package org.elasticmq.rest.sqs.aws

import org.elasticmq.{BinaryMessageAttribute, NumberMessageAttribute, StringMessageAttribute}
import org.elasticmq.rest.sqs.client._
import scala.concurrent.duration.DurationInt

trait MessageOperationsTests extends AmazonJavaSdkNewTestBase {

  test("should return error when message body is empty") {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get

    // expect
    assertError(testClient.sendMessage(queueUrl, ""), InvalidParameterValue, "MessageBody")
  }

  test("should return error when message body is too long") {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get

    // expect: In strict mode ElasticMQ should throw error for body > 256KB
    // But since the current server might not be in strict mode, we skip this for now or check if it fails
    // The previous test run showed it returned Right(()), so strict mode is not enabled.
    // If strict mode is enabled, it should be:
    assertError(testClient.sendMessage(queueUrl, "x" * (1024 * 1024 + 1)), InvalidParameterValue, "MessageTooLong")
  }

  test("should send and receive a simple message") {
    doTestSendAndReceiveMessageWithAttributes("test msg 123")
  }

  test("should send and receive a simple message with message attributes") {
    doTestSendAndReceiveMessageWithAttributes(
      "Message 1",
      Map(
        "red" -> StringMessageAttribute("fish"),
        "blue" -> StringMessageAttribute("cat"),
        "green" -> BinaryMessageAttribute("dog".getBytes("UTF-8")),
        "yellow" -> NumberMessageAttribute("1234567890"),
        "orange" -> NumberMessageAttribute("0987654321", Some("custom"))
      )
    )
  }

  test("should send a simple message with message attributes and only receive requested attributes") {
    doTestSendAndReceiveMessageWithAttributes(
      "Message 1",
      Map(
        "red" -> StringMessageAttribute("fish"),
        "blue" -> StringMessageAttribute("cat"),
        "green" -> BinaryMessageAttribute("dog".getBytes("UTF-8")),
        "yellow" -> NumberMessageAttribute("1234567890"),
        "orange" -> NumberMessageAttribute("0987654321", Some("custom"))
      ),
      List("red", "green", "orange")
    )
  }

  test("should send a simple message with message attributes and only receive no requested attributes by default") {
    doTestSendAndReceiveMessageWithAttributes(
      "Message 1",
      Map(
        "red" -> StringMessageAttribute("fish"),
        "blue" -> StringMessageAttribute("cat"),
        "green" -> BinaryMessageAttribute("dog".getBytes("UTF-8")),
        "yellow" -> NumberMessageAttribute("1234567890"),
        "orange" -> NumberMessageAttribute("0987654321", Some("custom"))
      ),
      List()
    )
  }

  test("should send and receive a simple message with message attributes and AWSTraceHeader") {
    val message = doTestSendAndReceiveMessageWithAttributes(
      "Message 1",
      Map(
        "red" -> StringMessageAttribute("fish"),
        "blue" -> StringMessageAttribute("cat"),
        "green" -> BinaryMessageAttribute("dog".getBytes("UTF-8")),
        "yellow" -> NumberMessageAttribute("1234567890"),
        "orange" -> NumberMessageAttribute("0987654321", Some("custom"))
      ),
      requestedAttributes = List("All"),
      awsTraceHeader = Some("abc-123"),
      requestedSystemAttributes = List("AWSTraceHeader")
    )

    message.attributes shouldBe Map(AWSTraceHeader -> "abc-123")
  }

  test("should send and receive 1MB message") {
    doTestSendAndReceiveMessageWithAttributes("x" * 1024 * 1024)
  }

  test("should fail send 1MB + 1 byte message") {
    val queue = testClient.createQueue("testQueue1").toOption.get
    val content = "x" * (1024 * 1024 + 1)
    val sendResult = testClient.sendMessage(queue, content)
    sendResult.isLeft shouldBe true // TODO: check error type returned by SQS
  }

  test("should fail if message is sent to missing queue") {
    testClient.sendMessage("http://localhost:9321/123456789012/missingQueue", "test123") shouldBe Left(
      SqsClientError(QueueDoesNotExist, "The specified queue does not exist.")
    )
  }

  test("should purge queue") {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get
    testClient.sendMessage(queueUrl, "test123")
    testClient.sendMessage(queueUrl, "test234")
    testClient.sendMessage(queueUrl, "test345")

    // when
    testClient.purgeQueue(queueUrl)

    // then
    eventually(timeout(5.seconds), interval(100.millis)) {
      testClient
        .getQueueAttributes(queueUrl, ApproximateNumberOfMessagesAttributeName)(
          ApproximateNumberOfMessagesAttributeName.value
        )
        .toInt shouldBe 0
    }
  }

  test("should send message batch") {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get

    // when
    val response = testClient
      .sendMessageBatch(queueUrl, List(SendMessageBatchEntry("1", "test123"), SendMessageBatchEntry("2", "")))
      .toOption
      .get

    // then
    response.successful should have size 1
    response.successful.head.id shouldBe "1"
    response.successful.head.messageId should not be empty
    response.successful.head.md5OfMessageBody should not be empty

    response.failed should have size 1
    response.failed.head.id shouldBe "2"
    response.failed.head.senderFault shouldBe true
    response.failed.head.code shouldBe "InvalidAttributeValue"
    response.failed.head.message shouldBe "The request must contain the parameter MessageBody."
  }

  test("should delete message") {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get
    testClient.sendMessage(queueUrl, "test123")
    val message = testClient.receiveMessage(queueUrl).head

    // when
    testClient.deleteMessage(queueUrl, message.receiptHandle)

    // then
    eventually(timeout(5.seconds), interval(100.millis)) {
      val attrs = testClient.getQueueAttributes(queueUrl, AllAttributeNames)
      attrs(ApproximateNumberOfMessagesAttributeName.value).toInt shouldBe 0
      attrs(ApproximateNumberOfMessagesNotVisibleAttributeName.value).toInt shouldBe 0
    }
  }

  test("should delete message batch") {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get
    testClient.sendMessage(queueUrl, "test123")
    testClient.sendMessage(queueUrl, "test234")
    testClient.sendMessage(queueUrl, "test345")
    val messages = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))

    // then
    messages should have size 3

    // when
    val result =
      testClient
        .deleteMessageBatch(
          queueUrl,
          List(
            DeleteMessageBatchEntry("A", messages.head.receiptHandle),
            DeleteMessageBatchEntry("B", messages(1).receiptHandle)
          )
        )
        .toOption
        .get

    // then
    result.successful should have size 2
    result.successful.map(_.id) shouldBe List("A", "B")
    result.failed shouldBe empty
  }

  test("should change message visibility") {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get
    testClient.sendMessage(queueUrl, "test123")
    val message = testClient.receiveMessage(queueUrl).head

    // when
    testClient.changeMessageVisibility(queueUrl, message.receiptHandle, 0)
    val messagesReceivedAgain = testClient.receiveMessage(queueUrl)

    // then
    messagesReceivedAgain should have size 1
  }

  test("should change message visibility batch") {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get
    testClient.sendMessage(queueUrl, "test123")
    testClient.sendMessage(queueUrl, "test234")
    testClient.sendMessage(queueUrl, "test345")
    val messages = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))

    // then
    messages should have size 3

    // when
    val result =
      testClient
        .changeMessageVisibilityBatch(
          queueUrl,
          List(
            ChangeMessageVisibilityBatchEntry("A", messages.head.receiptHandle, 0),
            ChangeMessageVisibilityBatchEntry("B", messages(2).receiptHandle, 0)
          )
        )
        .toOption
        .get
    val messagesReceivedAgain = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))

    // then
    result.successful should have size 2
    result.successful.map(_.id) shouldBe List("A", "B")
    result.failed shouldBe empty

    messagesReceivedAgain should have size 2
  }
}
