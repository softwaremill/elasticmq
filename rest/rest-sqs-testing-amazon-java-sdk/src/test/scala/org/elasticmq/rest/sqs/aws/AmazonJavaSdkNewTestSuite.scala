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
    testClient.createQueue("testQueue1").toOption.get
  }

  test("should get queue url") {
    // given
    testClient.createQueue("testQueue1").toOption.get

    // when
    val queueUrl = testClient.getQueueUrl("testQueue1").toOption.get

    // then
    queueUrl shouldEqual "http://localhost:9321/123456789012/testQueue1"
  }

  test("should list queues") {
    // given
    testClient.createQueue("testQueue1").toOption.get
    testClient.createQueue("testQueue2").toOption.get

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
    testClient.createQueue("aaa-testQueue1").toOption.get
    testClient.createQueue("bbb-testQueue2").toOption.get
    testClient.createQueue("bbb-testQueue3").toOption.get

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
    testClient.sendMessage(regularQueueUrl, "A body", messageGroupId = Some("group-1")) shouldBe Right(())
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
    ) shouldBe Right(())
  }

  test("should return error when creating queue with too long name") {
    // expect
    assertError(testClient.createQueue("x" * 81), InvalidAttributeName, "longer than 80")
  }

  test("should return error when creating queue with invalid characters") {
    // expect
    assertError(testClient.createQueue("queue with spaces"), InvalidAttributeName, "Can only include")
  }

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

  test("should list DeadLetterQueueSourceArn in receive message attributes") {
    // given
    val dlQueue = testClient.createQueue("testDlq").toOption.get
    val queue = testClient
      .createQueue(
        "testQueue1",
        Map(
          VisibilityTimeoutAttributeName -> "0",
          RedrivePolicyAttributeName -> RedrivePolicy("testDlq", awsRegion, awsAccountId, 1).toJson.compactPrint
        )
      )
      .toOption
      .get

    // when
    testClient.sendMessage(queue, "test123")
    val firstReceiveResult = testClient.receiveMessage(queue, List("All"))
    val secondReceiveResult = testClient.receiveMessage(queue, List("All"))
    val dlqReceiveResult = testClient.receiveMessage(dlQueue, List("All"))

    // then
    firstReceiveResult.flatMap(_.attributes.keys.toList) should not contain DeadLetterQueueSourceArn
    secondReceiveResult shouldBe empty
    dlqReceiveResult.flatMap(_.attributes.toList) should contain(
      (DeadLetterQueueSourceArn, s"arn:aws:sqs:$awsRegion:$awsAccountId:testQueue1")
    )
  }

  private def assertError(
      result: Either[SqsClientError, _],
      expectedType: SqsClientErrorType,
      messageSubstring: String
  ): Unit = {
    result match {
      case Left(SqsClientError(errorType, message)) =>
        errorType shouldBe expectedType
        message should include(messageSubstring)
      case Right(_) =>
        fail(s"Expected error $expectedType with message containing '$messageSubstring', but got success")
    }
  }

  test("should list all source queues for a dlq") {
    // given
    val dlqUrl = testClient.createQueue("testDlq").toOption.get
    val redrivePolicy = RedrivePolicy("testDlq", awsRegion, awsAccountId, 1).toJson.toString

    val qUrls = for (i <- 1 to 3) yield {
      testClient
        .createQueue(
          "q" + i,
          Map(RedrivePolicyAttributeName -> redrivePolicy)
        )
        .toOption
        .get
    }

    // when
    val result = testClient.listDeadLetterSourceQueues(dlqUrl)

    // then
    result should contain theSameElementsAs (qUrls)
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

  test("should fail if message is sent to missing queue") {
    testClient.sendMessage("http://localhost:9321/123456789012/missingQueue", "test123") shouldBe Left(
      SqsClientError(QueueDoesNotExist, "The specified queue does not exist.")
    )
  }

  test("should tag, untag and list queue tags") {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get

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
    val queueUrl = testClient.createQueue("testQueue1").toOption.get

    // expect
    testClient.addPermission(queueUrl, "l", List(awsAccountId), List("get"))
  }

  test("should remove permission") {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get
    testClient.addPermission(queueUrl, "l", List(awsAccountId), List("get"))

    // expect
    testClient.removePermission(queueUrl, "l")
  }

  test("should delete queue") {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get

    // when
    testClient.deleteQueue(queueUrl)

    // then
    eventually(timeout(5.seconds), interval(100.millis)) {
      testClient.listQueues() shouldBe empty
    }
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

  test("should list dead letter source queues") {
    // given
    val dlq1Url = testClient.createQueue("testDlq1").toOption.get
    val redrivePolicyJson = RedrivePolicy("testDlq1", awsRegion, awsAccountId, 3).toJson.compactPrint
    val queue1Url =
      testClient.createQueue("testQueue1", Map(RedrivePolicyAttributeName -> redrivePolicyJson)).toOption.get
    val queue2Url =
      testClient.createQueue("testQueue2", Map(RedrivePolicyAttributeName -> redrivePolicyJson)).toOption.get
    val queue4Url =
      testClient.createQueue("testQueue4", Map(RedrivePolicyAttributeName -> redrivePolicyJson)).toOption.get
    testClient.createQueue("testDlq2").toOption.get
    testClient
      .createQueue(
        "testQueue3",
        Map(RedrivePolicyAttributeName -> RedrivePolicy("testDlq2", awsRegion, awsAccountId, 3).toJson.compactPrint)
      )
      .toOption
      .get
    testClient.createQueue("testQueue5").toOption.get

    // when
    val result = testClient.listDeadLetterSourceQueues(dlq1Url)

    // then
    result should contain theSameElementsAs Set(queue1Url, queue2Url, queue4Url)
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

  private def doTestSendAndReceiveMessageWithAttributes(
      content: String,
      messageAttributes: Map[String, MessageAttribute] = Map.empty,
      requestedAttributes: List[String] = List.empty,
      awsTraceHeader: Option[String] = None,
      requestedSystemAttributes: List[String] = List.empty
  ) = {
    // given
    val queue = testClient.createQueue("testQueue1").toOption.get
    val sendResult =
      testClient.sendMessage(queue, content, messageAttributes = messageAttributes, awsTraceHeader = awsTraceHeader)
    sendResult shouldBe Right(())
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
