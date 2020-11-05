package org.elasticmq.rest.sqs

import com.amazonaws.services.sqs.model.{CreateQueueRequest, ReceiveMessageRequest, SendMessageRequest}
import org.scalatest.matchers.should.Matchers

import scala.collection.JavaConverters.{iterableAsScalaIterableConverter, mapAsScalaMapConverter}

class ReceiveMessageAttributesTest extends SqsClientServerCommunication with Matchers {

  val MessageDeduplicationIdKey = "MessageDeduplicationId"
  val MessageGroupIdKey = "MessageGroupId"

  test("For normal queues, MessageDeduplicationId and MessageGroupId should not be sent with ReceiveMessage response") {
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl
    client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))
    val messages =
      client.receiveMessage(new ReceiveMessageRequest(queueUrl).withAttributeNames("All")).getMessages.asScala

    messages should have size 1
    val messageAttributes = messages.head.getAttributes.asScala
    messageAttributes.get(MessageDeduplicationIdKey) should not be defined
    messageAttributes.get(MessageGroupIdKey) should not be defined
  }

  test("For FIFO queues, MessageDeduplicationId and MessageGroupId should be sent with ReceiveMessage response") {
    val queueUrl = client
      .createQueue(
        new CreateQueueRequest("testQueue1.fifo")
          .addAttributesEntry("FifoQueue", "true")
          .addAttributesEntry("ContentBasedDeduplication", "true")
      )
      .getQueueUrl
    client.sendMessage(
      new SendMessageRequest(queueUrl, "Message 1")
        .withMessageDeduplicationId("123")
        .withMessageGroupId("456")
    )
    val messages =
      client.receiveMessage(new ReceiveMessageRequest(queueUrl).withAttributeNames("All")).getMessages.asScala

    messages should have size 1
    val messageAttributes = messages.head.getAttributes.asScala
    messageAttributes.get(MessageDeduplicationIdKey) shouldBe Some("123")
    messageAttributes.get(MessageGroupIdKey) shouldBe Some("456")
  }

}
