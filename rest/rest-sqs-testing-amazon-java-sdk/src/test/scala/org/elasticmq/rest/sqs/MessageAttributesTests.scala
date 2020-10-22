package org.elasticmq.rest.sqs

import com.amazonaws.services.sqs.model.{AmazonSQSException, CreateQueueRequest, MessageAttributeValue, ReceiveMessageRequest, SendMessageBatchRequest, SendMessageBatchRequestEntry, SendMessageRequest}
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters.{ListHasAsScala, MapHasAsJava}

class MessageAttributesTests extends SqsClientServerCommunication with Matchers with OptionValues {

  test("Sending message with empty attribute value and String data type should result in error") {
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    val ex = the[AmazonSQSException] thrownBy client.sendMessage(
      new SendMessageRequest(queueUrl, "Message 1")
        .addMessageAttributesEntry("attribute1", new MessageAttributeValue().withStringValue("").withDataType("String"))
    )

    ex.getMessage should include("Attribute 'attribute1' must contain a non-empty value of type 'String")
  }

  test("Sending message with empty attribute value and Number data type should result in error") {
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    val ex = the[AmazonSQSException] thrownBy client.sendMessage(
      new SendMessageRequest(queueUrl, "Message 1")
        .addMessageAttributesEntry("attribute1", new MessageAttributeValue().withStringValue("").withDataType("Number"))
    )

    ex.getMessage should include("Attribute 'attribute1' must contain a non-empty value of type 'Number")
  }

  test("Sending message with empty attribute type should result in error") {
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    val ex = the[AmazonSQSException] thrownBy client.sendMessage(
      new SendMessageRequest(queueUrl, "Message 1")
        .addMessageAttributesEntry("attribute1", new MessageAttributeValue().withStringValue("value").withDataType(""))
    )

    ex.getMessage should include("Attribute 'attribute1' must contain a non-empty attribute type")
  }

  test(
    "Sending message in batch should result in accepting only those messages that do not have empty message attributes"
  ) {
    val queueUrl = client.createQueue(new CreateQueueRequest("testQueue1")).getQueueUrl

    val resp = client.sendMessageBatch(
      new SendMessageBatchRequest(queueUrl).withEntries(
        new SendMessageBatchRequestEntry("1", "Message 1").withMessageAttributes(
          Map(
            "attribute1" -> new MessageAttributeValue().withStringValue("value1").withDataType("String")
          ).asJava
        ),
        new SendMessageBatchRequestEntry("2", "Message 2").withMessageAttributes(
          Map(
            "attribute1" -> new MessageAttributeValue().withStringValue("").withDataType("String")
          ).asJava
        ),
        new SendMessageBatchRequestEntry("3", "Message 3").withMessageAttributes(
          Map(
            "attribute1" -> new MessageAttributeValue().withStringValue("value1").withDataType("String")
          ).asJava
        )
      )
    )

    resp.getSuccessful should have size 2

    val receiveMessageResponse =
      client.receiveMessage(new ReceiveMessageRequest().withQueueUrl(queueUrl).withMaxNumberOfMessages(3))
    receiveMessageResponse.getMessages.asScala should have size 2
    receiveMessageResponse.getMessages.asScala
      .map(message => message.getBody)
      .foreach(messageBody => {
        messageBody should (be("Message 1") or be("Message 3"))
      })
  }

}
