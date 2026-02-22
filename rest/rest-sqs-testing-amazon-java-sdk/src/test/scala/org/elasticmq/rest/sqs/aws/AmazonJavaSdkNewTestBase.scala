package org.elasticmq.rest.sqs.aws

import org.elasticmq.rest.sqs.AwsConfig
import org.elasticmq.rest.sqs.client._
import org.elasticmq.MessageAttribute
import org.scalatest.concurrent.Eventually
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

trait AmazonJavaSdkNewTestBase
    extends AnyFunSuite
    with HasSqsTestClient
    with AwsConfig
    with Matchers
    with Eventually {

  protected def assertError(
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

  protected def doTestSendAndReceiveMessageWithAttributes(
      content: String,
      messageAttributes: Map[String, MessageAttribute] = Map.empty,
      requestedAttributes: List[String] = List.empty,
      awsTraceHeader: Option[String] = None,
      requestedSystemAttributes: List[String] = List.empty
  ): ReceivedMessage = {
    // given
    val queue = testClient.createQueue("testQueue1").toOption.get
    val sendResult =
      testClient.sendMessage(queue, content, messageAttributes = messageAttributes, awsTraceHeader = awsTraceHeader)
    sendResult.isRight shouldBe true
    val message = receiveSingleMessageObject(queue, requestedAttributes, requestedSystemAttributes).orNull

    // then
    message.body shouldBe content
    checkMessageAttributesMatchRequestedAttributes(messageAttributes, requestedAttributes, message)

    message
  }

  protected def receiveSingleMessageObject(
      queueUrl: String,
      requestedAttributes: List[String],
      requestedSystemAttributes: List[String]
  ): Option[ReceivedMessage] = {
    testClient
      .receiveMessage(queueUrl, systemAttributes = requestedSystemAttributes, messageAttributes = requestedAttributes)
      .headOption
  }

  protected def checkMessageAttributesMatchRequestedAttributes(
      messageAttributes: Map[String, MessageAttribute],
      requestedAttributes: List[String],
      message: ReceivedMessage
  ): Unit = {
    val filteredMessageAttributes = filterBasedOnRequestedAttributes(requestedAttributes, messageAttributes)
    message.messageAttributes should be(filteredMessageAttributes)
  }

  protected def filterBasedOnRequestedAttributes[T](
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
