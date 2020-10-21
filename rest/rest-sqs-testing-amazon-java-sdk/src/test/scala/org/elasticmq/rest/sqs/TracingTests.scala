package org.elasticmq.rest.sqs

import com.amazonaws.services.sqs.model.{
  CreateQueueRequest,
  MessageAttributeValue,
  MessageSystemAttributeValue,
  ReceiveMessageRequest,
  SendMessageBatchRequest,
  SendMessageBatchRequestEntry,
  SendMessageRequest
}
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers

import scala.collection.JavaConverters.{
  iterableAsScalaIterableConverter,
  mapAsScalaMapConverter,
  seqAsJavaListConverter
}

class TracingTests extends SqsClientServerCommunication with Matchers with OptionValues {

  val AWSTraceHeaderAttribute = "AWSTraceHeader"
  def systemAttributeValue(index: Int) = s"MessageSystemAttribute.$index.Value"
  def systemAttributeName(index: Int) = s"MessageSystemAttribute.$index.Name"
  def systemAttributeDataType(index: Int) = s"MessageSystemAttribute.$index.Value.DataType"

  test("Message should not have assigned trace if such one was not provided") {
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))

    val message =
      client
        .receiveMessage(new ReceiveMessageRequest(queueUrl).withAttributeNames("All").withMaxNumberOfMessages(1))
        .getMessages
        .asScala
        .head
    message.getAttributes.asScala.get(AWSTraceHeaderAttribute) shouldBe None
  }

  test("While sending message it should have assigned trace ID if it was provided as a system attribute") {
    val queueUrl = client
      .createQueue(new CreateQueueRequest("testQueue1"))
      .getQueueUrl

    client.sendMessage(
      new SendMessageRequest(queueUrl, "Message 1").addMessageSystemAttributesEntry(
        AWSTraceHeaderAttribute,
        new MessageSystemAttributeValue().withStringValue("123456789").withDataType("String")
      )
    )

    val message =
      client
        .receiveMessage(new ReceiveMessageRequest(queueUrl).withAttributeNames("All").withMaxNumberOfMessages(1))
        .getMessages
        .asScala
        .head
    message.getAttributes.asScala.get(AWSTraceHeaderAttribute) shouldBe Some("123456789")
  }

  test("While sending message it should have assigned trace if it was provided in a request header") {
    val queueUrl = client
      .createQueue(new CreateQueueRequest("testQueue1"))
      .getQueueUrl
    val request = new SendMessageRequest(queueUrl, "Message 1")
    request.putCustomRequestHeader("X-Amzn-Trace-Id", "987654321")

    client.sendMessage(request)

    val message =
      client
        .receiveMessage(new ReceiveMessageRequest(queueUrl).withAttributeNames("All").withMaxNumberOfMessages(1))
        .getMessages
        .asScala
        .head
    message.getAttributes.asScala.get(AWSTraceHeaderAttribute) shouldBe Some("987654321")
  }

  test(
    "Sending several messages in batch request and applying trace ID in system attributes per message should result in different traces assigned to different messages"
  ) {
    val queueUrl = client
      .createQueue(new CreateQueueRequest("testQueue1"))
      .getQueueUrl

    client.sendMessageBatch(
      new SendMessageBatchRequest(
        queueUrl,
        List(
          new SendMessageBatchRequestEntry()
            .withMessageBody("message 1")
            .withId("id1")
            .addMessageSystemAttributesEntry(
              AWSTraceHeaderAttribute,
              new MessageSystemAttributeValue().withStringValue("1").withDataType("String")
            ),
          new SendMessageBatchRequestEntry()
            .withMessageBody("message 2")
            .withId("id2")
            .addMessageSystemAttributesEntry(
              AWSTraceHeaderAttribute,
              new MessageSystemAttributeValue().withStringValue("2").withDataType("String")
            )
        ).asJava
      )
    )

    val messages =
      client
        .receiveMessage(new ReceiveMessageRequest(queueUrl).withAttributeNames("All").withMaxNumberOfMessages(2))
        .getMessages
        .asScala
        .toList

    messages.find(_.getBody == "message 1").value.getAttributes.asScala.get(AWSTraceHeaderAttribute) shouldBe Some("1")
    messages.find(_.getBody == "message 2").value.getAttributes.asScala.get(AWSTraceHeaderAttribute) shouldBe Some("2")
  }

  test(
    "Sending several messages in batch request and applying trace ID as a header to request, should result in assigning same trace ID to all messages in batch"
  ) {
    val queueUrl = client
      .createQueue(new CreateQueueRequest("testQueue1"))
      .getQueueUrl

    val request = new SendMessageBatchRequest(
      queueUrl,
      List(
        new SendMessageBatchRequestEntry().withMessageBody("message 1").withId("id1"),
        new SendMessageBatchRequestEntry().withMessageBody("message 2").withId("id2")
      ).asJava
    )
    request.putCustomRequestHeader("X-Amzn-Trace-Id", "987654321")

    client.sendMessageBatch(request)

    val messages =
      client
        .receiveMessage(new ReceiveMessageRequest(queueUrl).withAttributeNames("All").withMaxNumberOfMessages(2))
        .getMessages
        .asScala
        .toList

    messages.find(_.getBody == "message 1").value.getAttributes.asScala.get(AWSTraceHeaderAttribute) shouldBe Some(
      "987654321"
    )
    messages.find(_.getBody == "message 2").value.getAttributes.asScala.get(AWSTraceHeaderAttribute) shouldBe Some(
      "987654321"
    )
  }

  test("Trace ID provided as a system attribute should have precedence over trace ID provided in request header") {
    val queueUrl = client
      .createQueue(new CreateQueueRequest("testQueue1"))
      .getQueueUrl
    val request = new SendMessageRequest(queueUrl, "Message 1").addMessageSystemAttributesEntry(
      AWSTraceHeaderAttribute,
      new MessageSystemAttributeValue().withStringValue("123456789").withDataType("String")
    )
    request.putCustomRequestHeader("X-Amzn-Trace-Id", "987654321")

    client.sendMessage(request)

    val message =
      client
        .receiveMessage(new ReceiveMessageRequest(queueUrl).withAttributeNames("All").withMaxNumberOfMessages(1))
        .getMessages
        .asScala
        .head
    message.getAttributes.asScala.get(AWSTraceHeaderAttribute) shouldBe Some("123456789")
  }

  test("Client should be able to ask only for Trace ID attribute while retrieving message") {
    val queueUrl = client
      .createQueue(new CreateQueueRequest("testQueue1"))
      .getQueueUrl
    val request = new SendMessageRequest(queueUrl, "Message 1")
      .addMessageSystemAttributesEntry(
        AWSTraceHeaderAttribute,
        new MessageSystemAttributeValue().withStringValue("123456789").withDataType("String")
      )
      .addMessageAttributesEntry(
        "randomAttributeName",
        new MessageAttributeValue().withStringValue("randomValue").withDataType("String")
      )

    request.putCustomRequestHeader("X-Amzn-Trace-Id", "987654321")

    client.sendMessage(request)

    val message =
      client
        .receiveMessage(
          new ReceiveMessageRequest(queueUrl).withAttributeNames(AWSTraceHeaderAttribute).withMaxNumberOfMessages(1)
        )
        .getMessages
        .asScala
        .head
    message.getAttributes should have size 1
    message.getAttributes.asScala.get(AWSTraceHeaderAttribute) shouldBe Some("123456789")
  }

}
