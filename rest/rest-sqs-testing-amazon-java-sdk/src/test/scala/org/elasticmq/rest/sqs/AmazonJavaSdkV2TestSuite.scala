package org.elasticmq.rest.sqs

import org.elasticmq.{BinaryMessageAttribute, MessageAttribute, NumberMessageAttribute, StringMessageAttribute}
import org.scalatest.matchers.should.Matchers

import scala.collection.JavaConverters._
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.sqs.model._
import software.amazon.awssdk.services.sqs.model.{GetQueueUrlRequest => AwsSdkGetQueueUrlRequest}

class AmazonJavaSdkV2TestSuite extends SqsClientServerWithSdkV2Communication with Matchers {

  test("should create a queue") {
    clientV2.createQueue(CreateQueueRequest.builder().queueName("testQueue1").build())
  }

  test("should get queue url") {
    // Given
    clientV2.createQueue(CreateQueueRequest.builder().queueName("testQueue1").build())

    // When
    val queueUrl = clientV2.getQueueUrl(AwsSdkGetQueueUrlRequest.builder().queueName("testQueue1").build()).queueUrl()

    // Then
    queueUrl shouldEqual "http://localhost:9321/123456789012/testQueue1"
  }

  test("should fail to get queue url if queue doesn't exist") {
    // When
    val thrown = intercept[QueueDoesNotExistException] {
      clientV2.getQueueUrl(AwsSdkGetQueueUrlRequest.builder().queueName("testQueue1").build()).queueUrl()
    }

    // Then
    thrown.awsErrorDetails().errorCode() shouldBe "QueueDoesNotExist"
    thrown.awsErrorDetails().errorMessage() shouldBe "The specified queue does not exist."
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
        // affected by https://github.com/softwaremill/elasticmq/issues/946
        // "green" -> BinaryMessageAttribute("dog".getBytes("UTF-8")),
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
        // affected by https://github.com/softwaremill/elasticmq/issues/946
        // "green" -> BinaryMessageAttribute("dog".getBytes("UTF-8")),
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
        // affected by https://github.com/softwaremill/elasticmq/issues/946
        // "green" -> BinaryMessageAttribute("dog".getBytes("UTF-8")),
        "yellow" -> NumberMessageAttribute("1234567890"),
        "orange" -> NumberMessageAttribute("0987654321", Some("custom"))
      ),
      List()
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
    val queue = clientV2.createQueue(CreateQueueRequest.builder().queueName("testQueue1").build())

    // When
    val attributes = messageAttributes.map {
      case (k, v: StringMessageAttribute) =>
        k -> MessageAttributeValue.builder().dataType(v.getDataType()).stringValue(v.stringValue).build()
      case (k, v: NumberMessageAttribute) =>
        k -> MessageAttributeValue.builder().dataType(v.getDataType()).stringValue(v.stringValue).build()
      case (k, v: BinaryMessageAttribute) =>
        k -> MessageAttributeValue
          .builder()
          .dataType(v.getDataType())
          .binaryValue(SdkBytes.fromByteArray(v.binaryValue))
          .build()
    }

    val sendMessageRequest = SendMessageRequest
      .builder()
      .queueUrl(queue.queueUrl())
      .messageBody(content)
      .messageAttributes(attributes.asJava)
      .build()

    clientV2.sendMessage(sendMessageRequest)

    val message = receiveSingleMessageObject(queue.queueUrl(), requestedAttributes).orNull

    // Then
    message.body() shouldBe content
    checkMessageAttributesMatchRequestedAttributes(messageAttributes, requestedAttributes, sendMessageRequest, message)
  }

  private def receiveSingleMessageObject(queueUrl: String, requestedAttributes: List[String]): Option[Message] = {
    clientV2
      .receiveMessage(
        ReceiveMessageRequest
          .builder()
          .queueUrl(queueUrl)
          .messageAttributeNames(requestedAttributes.asJava)
          .build()
      )
      .messages
      .asScala
      .headOption
  }

  private def checkMessageAttributesMatchRequestedAttributes(
                                                              messageAttributes: Map[String, MessageAttribute],
                                                              requestedAttributes: List[String],
                                                              sendMessageRequest: SendMessageRequest,
                                                              message: Message
                                                            ) = {
    val filteredSendMessageAttr =
      filterBasedOnRequestedAttributes(requestedAttributes, sendMessageRequest.messageAttributes.asScala.toMap).asJava
    val filteredMessageAttributes = filterBasedOnRequestedAttributes(requestedAttributes, messageAttributes)

    message.messageAttributes should be(filteredSendMessageAttr)
    message.messageAttributes.asScala.map { case (k, attr) =>
      (
        k,
        if (attr.dataType.startsWith("String") && attr.stringValue != null) {
          StringMessageAttribute(attr.stringValue).stringValue
        } else if (attr.dataType.startsWith("Number") && attr.stringValue != null) {
          NumberMessageAttribute(attr.stringValue).stringValue
        } else {
          BinaryMessageAttribute.fromByteBuffer(attr.binaryValue.asByteBuffer()).asBase64
        }
      )
    } should be(filteredMessageAttributes.map { case (k, attr) =>
      (
        k,
        attr match {
          case s: StringMessageAttribute => s.stringValue
          case n: NumberMessageAttribute => n.stringValue
          case b: BinaryMessageAttribute => b.asBase64
        }
      )
    })
  }

  private def filterBasedOnRequestedAttributes[T](
                                                   requestedAttributes: List[String],
                                                   messageAttributes: Map[String, T]
                                                 ): Map[String, T] = {
    if (requestedAttributes.contains("All")) {
      messageAttributes
    } else {
      messageAttributes.view.filterKeys(k => requestedAttributes.contains(k)).toMap
    }
  }
}
