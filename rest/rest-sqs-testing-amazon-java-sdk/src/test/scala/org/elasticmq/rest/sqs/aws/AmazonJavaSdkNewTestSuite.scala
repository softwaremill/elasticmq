package org.elasticmq.rest.sqs.aws

import org.elasticmq.rest.sqs.client._
import org.elasticmq.rest.sqs.model.RedrivePolicy
import org.elasticmq.rest.sqs.model.RedrivePolicyJson.format
import org.elasticmq.rest.sqs.{AwsConfig, SqsClientServerCommunication, SqsClientServerWithSdkV2Communication, client}
import org.elasticmq.{BinaryMessageAttribute, MessageAttribute, NumberMessageAttribute, StringMessageAttribute}
import org.scalatest.concurrent.Eventually
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import spray.json.enrichAny

import scala.concurrent.duration.DurationInt

abstract class AmazonJavaSdkNewTestSuite
    extends AnyFunSuite
    with HasSqsTestClient
    with AwsConfig
    with Matchers
    with Eventually {

  test("should create a queue") {
    testClient.createQueue("testQueue1")
  }

  test("should get queue url") {
    // given
    testClient.createQueue("testQueue1")

    // when
    val queueUrl = testClient.getQueueUrl("testQueue1").toOption.get

    // then
    queueUrl shouldEqual "http://localhost:9321/123456789012/testQueue1"
  }

  test("should list queues") {
    // given
    testClient.createQueue("testQueue1")
    testClient.createQueue("testQueue2")

    // when
    val queueUrls = testClient.listQueues()

    // then
    queueUrls shouldBe List(
      "http://localhost:9321/123456789012/testQueue1",
      "http://localhost:9321/123456789012/testQueue2"
    )
  }

  test("should list queues with specified prefix") {
    // given
    testClient.createQueue("aaa-testQueue1")
    testClient.createQueue("bbb-testQueue2")
    testClient.createQueue("bbb-testQueue3")

    // when
    val queueUrls = testClient.listQueues(Some("bbb"))

    // then
    queueUrls shouldBe List(
      "http://localhost:9321/123456789012/bbb-testQueue2",
      "http://localhost:9321/123456789012/bbb-testQueue3"
    )
  }

  test("should fail to get queue url if queue doesn't exist") {
    testClient.getQueueUrl("testQueue1") shouldBe Left(
      SqsClientError(QueueDoesNotExist, "The specified queue does not exist.")
    )
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

  test("should return DeadLetterQueueSourceArn in receive message attributes") {
    // given
    testClient.createQueue("testDlq")
    val queue = testClient.createQueue(
      "testQueue1",
      Map(RedrivePolicyAttributeName -> RedrivePolicy("testDlq", awsRegion, awsAccountId, 1).toJson.compactPrint)
    )

    // when
    testClient.sendMessage(queue, "test123")
    val receiveResult = testClient.receiveMessage(queue, List("All"))

    // then
    receiveResult.flatMap(_.attributes.toList) should contain(
      (DeadLetterQueueSourceArn, s"arn:aws:sqs:$awsRegion:$awsAccountId:testDlq")
    )
  }

  test("should fail if message is sent to missing queue") {
    testClient.sendMessage("http://localhost:9321/123456789012/missingQueue", "test123") shouldBe Left(
      SqsClientError(QueueDoesNotExist, "The specified queue does not exist.")
    )
  }

  test("should tag, untag and list queue tags") {
    // given
    val queueUrl = testClient.createQueue("testQueue1")

    // when
    testClient.tagQueue(queueUrl, Map("tag1" -> "value1", "tag2" -> "value2"))

    // then
    testClient.listQueueTags(queueUrl) shouldBe Map("tag1" -> "value1", "tag2" -> "value2")

    // when
    testClient.untagQueue(queueUrl, List("tag1"))

    // then
    testClient.listQueueTags(queueUrl) shouldBe Map("tag2" -> "value2")
  }

  test("should add permission") {
    // given
    val queueUrl = testClient.createQueue("testQueue1")

    // expect
    testClient.addPermission(queueUrl, "l", List(awsAccountId), List("get"))
  }

  test("should remove permission") {
    // given
    val queueUrl = testClient.createQueue("testQueue1")
    testClient.addPermission(queueUrl, "l", List(awsAccountId), List("get"))

    // expect
    testClient.removePermission(queueUrl, "l")
  }

  test("should delete queue") {
    // given
    val queueUrl = testClient.createQueue("testQueue1")

    // when
    testClient.deleteQueue(queueUrl)

    // then
    eventually(timeout(5.seconds), interval(100.millis)) {
      testClient.listQueues() shouldBe empty
    }
  }

  test("should purge queue") {
    // given
    val queueUrl = testClient.createQueue("testQueue1")
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
    val queueUrl = testClient.createQueue("testQueue1")

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
    val queueUrl = testClient.createQueue("testQueue1")
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
    val queueUrl = testClient.createQueue("testQueue1")
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
    val queueUrl = testClient.createQueue("testQueue1")
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
    val queueUrl = testClient.createQueue("testQueue1")
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

  test("should list dead letter source queues") {
    // given
    val dlq1Url = testClient.createQueue("testDlq1")
    val redrivePolicyJson = RedrivePolicy("testDlq1", awsRegion, awsAccountId, 3).toJson.compactPrint
    val queue1Url = testClient.createQueue("testQueue1", Map(RedrivePolicyAttributeName -> redrivePolicyJson))
    val queue2Url = testClient.createQueue("testQueue2", Map(RedrivePolicyAttributeName -> redrivePolicyJson))
    val queue4Url = testClient.createQueue("testQueue4", Map(RedrivePolicyAttributeName -> redrivePolicyJson))
    testClient.createQueue("testDlq2")
    testClient.createQueue(
      "testQueue3",
      Map(RedrivePolicyAttributeName -> RedrivePolicy("testDlq2", awsRegion, awsAccountId, 3).toJson.compactPrint)
    )
    testClient.createQueue("testQueue5")

    // when
    val result = testClient.listDeadLetterSourceQueues(dlq1Url)

    // then
    result should contain theSameElementsAs Set(queue1Url, queue2Url, queue4Url)
  }

  test("should receive system all attributes") {
    // given
    val queueUrl = testClient.createQueue(
      "testQueue2.fifo",
      Map(FifoQueueAttributeName -> "true", ContentBasedDeduplicationAttributeName -> "false")
    )
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
      testClient.createQueue("testQueue1.fifo", Map(FifoQueueAttributeName -> "true", DelaySecondsAttributeName -> "5"))
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

  private def doTestSendAndReceiveMessageWithAttributes(
      content: String,
      messageAttributes: Map[String, MessageAttribute] = Map.empty,
      requestedAttributes: List[String] = List.empty,
      awsTraceHeader: Option[String] = None,
      requestedSystemAttributes: List[String] = List.empty
  ) = {
    // given
    val queue = testClient.createQueue("testQueue1")
    testClient.sendMessage(queue, content, messageAttributes = messageAttributes, awsTraceHeader = awsTraceHeader)
    val message = receiveSingleMessageObject(queue, requestedAttributes, requestedSystemAttributes).orNull

    // then
    message.body shouldBe content
    checkMessageAttributesMatchRequestedAttributes(messageAttributes, requestedAttributes, message)

    message
  }

  private def receiveSingleMessageObject(
      queueUrl: String,
      requestedAttributes: List[String],
      requestedSystemAttributes: List[String]
  ): Option[client.ReceivedMessage] = {
    testClient
      .receiveMessage(queueUrl, systemAttributes = requestedSystemAttributes, messageAttributes = requestedAttributes)
      .headOption
  }

  private def checkMessageAttributesMatchRequestedAttributes(
      messageAttributes: Map[String, MessageAttribute],
      requestedAttributes: List[String],
      message: client.ReceivedMessage
  ) = {
    val filteredMessageAttributes = filterBasedOnRequestedAttributes(requestedAttributes, messageAttributes)
    message.messageAttributes should be(filteredMessageAttributes)
  }

  private def filterBasedOnRequestedAttributes[T](
      requestedAttributes: List[String],
      messageAttributes: Map[String, T]
  ): Map[String, T] = {
    if (requestedAttributes.contains("All")) {
      messageAttributes
    } else {
      messageAttributes.filterKeys(k => requestedAttributes.contains(k)).toMap
    }
  }
}

class AmazonJavaSdkV1TestSuite extends AmazonJavaSdkNewTestSuite with SqsClientServerCommunication
class AmazonJavaSdkV2TestSuite extends AmazonJavaSdkNewTestSuite with SqsClientServerWithSdkV2Communication
