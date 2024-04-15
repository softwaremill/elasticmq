package org.elasticmq.rest.sqs

import org.elasticmq.rest.sqs.client.{
  DeadLetterQueueSourceArn,
  HasSqsTestClient,
  QueueDoesNotExist,
  RedrivePolicyAttributeName,
  SqsClientError
}
import org.elasticmq.rest.sqs.model.RedrivePolicy
import org.elasticmq.{BinaryMessageAttribute, MessageAttribute, NumberMessageAttribute, StringMessageAttribute}
import org.elasticmq.rest.sqs.model.RedrivePolicyJson.format
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import spray.json.enrichAny

import scala.collection.JavaConverters._

abstract class AmazonJavaSdkNewTestSuite extends AnyFunSuite with HasSqsTestClient with AwsConfig with Matchers {

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

  test("should fail to get queue url if queue doesn't exist") {
    testClient.getQueueUrl("testQueue1") shouldBe Left(
      SqsClientError(QueueDoesNotExist, "The specified queue does not exist.")
    )
  }

  test("should send and receive a simple message") {
    doTestSendAndReceiveMessage("test msg 123")
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

  private def doTestSendAndReceiveMessage(content: String): Unit = {
    doTestSendAndReceiveMessageWithAttributes(content, Map(), List())
  }

  private def doTestSendAndReceiveMessageWithAttributes(
      content: String,
      messageAttributes: Map[String, MessageAttribute]
  ): Unit = {
    doTestSendAndReceiveMessageWithAttributes(content, messageAttributes, List("All"))
  }

  private def doTestSendAndReceiveMessageWithAttributes(
      content: String,
      messageAttributes: Map[String, MessageAttribute],
      requestedAttributes: List[String]
  ): Unit = {
    // Given
    val queue = testClient.createQueue("testQueue1")
    testClient.sendMessage(queue, content, messageAttributes)
    val message = receiveSingleMessageObject(queue, requestedAttributes).orNull

    // Then
    message.body shouldBe content
    checkMessageAttributesMatchRequestedAttributes(messageAttributes, requestedAttributes, message)
  }

  private def receiveSingleMessageObject(
      queueUrl: String,
      requestedAttributes: List[String]
  ): Option[client.ReceivedMessage] = {
    testClient
      .receiveMessage(queueUrl, messageAttributes = requestedAttributes)
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
