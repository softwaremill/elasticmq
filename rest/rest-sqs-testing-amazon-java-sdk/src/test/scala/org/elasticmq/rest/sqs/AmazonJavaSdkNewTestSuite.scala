package org.elasticmq.rest.sqs

import org.elasticmq.rest.sqs.client._
import org.elasticmq.rest.sqs.model.RedrivePolicy
import org.elasticmq.rest.sqs.model.RedrivePolicyJson.format
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
    // Given
    testClient.createQueue("testQueue1")

    // When
    val queueUrl = testClient.getQueueUrl("testQueue1").toOption.get

    // Then
    queueUrl shouldEqual "http://localhost:9321/123456789012/testQueue1"
  }

  test("should list queues") {
    // Given
    testClient.createQueue("testQueue1")
    testClient.createQueue("testQueue2")

    // When
    val queueUrls = testClient.listQueues()

    // Then
    queueUrls shouldBe List(
      "http://localhost:9321/123456789012/testQueue1",
      "http://localhost:9321/123456789012/testQueue2"
    )
  }

  test("should list queues with specified prefix") {
    // Given
    testClient.createQueue("aaa-testQueue1")
    testClient.createQueue("bbb-testQueue2")
    testClient.createQueue("bbb-testQueue3")

    // When
    val queueUrls = testClient.listQueues(Some("bbb"))

    // Then
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

  test("should fail if message is sent to missing queue") {
    testClient.sendMessage("http://localhost:9321/123456789012/missingQueue", "test123") shouldBe Left(
      SqsClientError(QueueDoesNotExist, "The specified queue does not exist.")
    )
  }

  test("should tag, untag and list queue tags") {
    // Given
    val queueUrl = testClient.createQueue("testQueue1")

    // When
    testClient.tagQueue(queueUrl, Map("tag1" -> "value1", "tag2" -> "value2"))

    // Then
    testClient.listQueueTags(queueUrl) shouldBe Map("tag1" -> "value1", "tag2" -> "value2")

    // When
    testClient.untagQueue(queueUrl, List("tag1"))

    // Then
    testClient.listQueueTags(queueUrl) shouldBe Map("tag2" -> "value2")
  }

  test("should add permission") {
    // Given
    val queueUrl = testClient.createQueue("testQueue1")

    // Expect
    testClient.addPermission(queueUrl, "l", List(awsAccountId), List("get"))
  }

  test("should remove permission") {
    // Given
    val queueUrl = testClient.createQueue("testQueue1")
    testClient.addPermission(queueUrl, "l", List(awsAccountId), List("get"))

    // Expect
    testClient.removePermission(queueUrl, "l")
  }

  test("should delete queue") {
    // Given
    val queueUrl = testClient.createQueue("testQueue1")

    // When
    testClient.deleteQueue(queueUrl)

    // Then
    eventually(timeout(5.seconds), interval(100.millis)) {
      testClient.listQueues() shouldBe empty
    }
  }

  test("should purge queue") {
    // Given
    val queueUrl = testClient.createQueue("testQueue1")
    testClient.sendMessage(queueUrl, "test123")
    testClient.sendMessage(queueUrl, "test234")
    testClient.sendMessage(queueUrl, "test345")

    // When
    testClient.purgeQueue(queueUrl)

    // Then
    eventually(timeout(5.seconds), interval(100.millis)) {
      testClient
        .getQueueAttributes(queueUrl, ApproximateNumberOfMessagesAttributeName)(
          ApproximateNumberOfMessagesAttributeName.value
        )
        .toInt shouldBe 0
    }
  }

  test("should return DeadLetterQueueSourceArn in receive message attributes") {
    // Given
    testClient.createQueue("testDlq")
    val queue = testClient.createQueue(
      "testQueue1",
      Map(RedrivePolicyAttributeName -> RedrivePolicy("testDlq", awsRegion, awsAccountId, 1).toJson.compactPrint)
    )

    // When
    testClient.sendMessage(queue, "test123")
    val receiveResult = testClient.receiveMessage(queue, List("All"))

    // Then
    receiveResult.flatMap(_.attributes.toList) should contain(
      (DeadLetterQueueSourceArn, s"arn:aws:sqs:$awsRegion:$awsAccountId:testDlq")
    )
  }

  private def doTestSendAndReceiveMessageWithAttributes(
      content: String,
      messageAttributes: Map[String, MessageAttribute] = Map.empty,
      requestedAttributes: List[String] = List.empty,
      awsTraceHeader: Option[String] = None,
      requestedSystemAttributes: List[String] = List.empty
  ) = {
    // Given
    val queue = testClient.createQueue("testQueue1")
    testClient.sendMessage(queue, content, messageAttributes, awsTraceHeader)
    val message = receiveSingleMessageObject(queue, requestedAttributes, requestedSystemAttributes).orNull

    // Then
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
