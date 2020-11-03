package org.elasticmq.rest.sqs

import com.amazonaws.services.sqs.model.{
  CreateQueueRequest,
  MessageAttributeValue,
  ReceiveMessageRequest,
  SendMessageRequest
}
import org.elasticmq.rest.sqs.model.RedrivePolicy
import org.elasticmq.rest.sqs.model.RedrivePolicyJson.format
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import spray.json.enrichAny

import scala.collection.JavaConverters._

class FifoDeduplicationTests extends SqsClientServerCommunication with Matchers with OptionValues {

  test("FIFO provided message deduplication ids should take priority over content based deduplication") {
    // Given
    val queueUrl = createFifoQueue()

    // When
    client.sendMessage(new SendMessageRequest(queueUrl, "body").withMessageDeduplicationId("1").withMessageGroupId("1"))
    client.sendMessage(new SendMessageRequest(queueUrl, "body").withMessageDeduplicationId("2").withMessageGroupId("1"))
    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(2)).getMessages

    // Then
    messages should have size 2
  }

  test("Queues should deduplicate messages based on the message body") {
    // Given
    val queueUrl = createFifoQueue()

    // When
    val sentMessages = for (i <- 1 to 10) yield {
      client.sendMessage(new SendMessageRequest(queueUrl, "Message").withMessageGroupId(s"$i"))
    }
    val messages1 =
      client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(4)).getMessages.asScala
    client.deleteMessage(queueUrl, messages1.head.getReceiptHandle)
    val messages2 =
      client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(4)).getMessages.asScala

    // Then
    sentMessages.map(_.getMessageId).toSet should have size 1
    messages1.map(_.getBody) should have size 1
    messages1.map(_.getBody).toSet should be(Set("Message"))
    messages2 should have size 0
  }

  test("Queues should deduplicate messages based on the message deduplication attribute") {
    val queueUrl = createFifoQueue()

    // When
    for (i <- 1 to 10) {
      client.sendMessage(
        new SendMessageRequest(queueUrl, s"Message $i")
          .withMessageDeduplicationId("DedupId")
          .withMessageGroupId("1")
      )
    }

    val m1 = receiveSingleMessageObject(queueUrl)
    client.deleteMessage(queueUrl, m1.get.getReceiptHandle)
    val m2 = receiveSingleMessage(queueUrl)

    // Then
    m1.map(_.getBody) should be(Some("Message 1"))
    m2 should be(empty)
  }

  test(
    "MD5 of body and attributes of messages should be calculated independently even if they have same deduplication ID"
  ) {
    def createMsgAttribute(attributeName: String) = {
      val attributeValue = new MessageAttributeValue()
      attributeValue.setDataType("String")
      attributeValue.setStringValue("value")
      Map(attributeName -> attributeValue).asJava
    }

    val queueUrl = createFifoQueue()

    val msg1 = client.sendMessage(
      new SendMessageRequest(queueUrl, s"Message 1")
        .withMessageDeduplicationId("DedupId")
        .withMessageGroupId("1")
        .withMessageAttributes(createMsgAttribute("attribute1"))
    )
    val msg2 = client.sendMessage(
      new SendMessageRequest(queueUrl, s"Message 2")
        .withMessageDeduplicationId("DedupId")
        .withMessageGroupId("1")
        .withMessageAttributes(createMsgAttribute("attribute2"))
    )
    val msg3 = client.sendMessage(
      new SendMessageRequest(queueUrl, s"Message 3")
        .withMessageDeduplicationId("DedupId")
        .withMessageGroupId("1")
        .withMessageAttributes(createMsgAttribute("attribute3"))
    )

    msg1.getMD5OfMessageBody shouldNot be(msg2.getMD5OfMessageBody)
    msg2.getMD5OfMessageBody shouldNot be(msg3.getMD5OfMessageBody)

    msg1.getMD5OfMessageAttributes shouldNot be(msg2.getMD5OfMessageAttributes)
    msg2.getMD5OfMessageAttributes shouldNot be(msg3.getMD5OfMessageAttributes)

    msg1.getMessageId shouldBe msg2.getMessageId
    msg2.getMessageId shouldBe msg3.getMessageId
  }

  test(
    "Message with deduplication ID was moved to DLQ and second message with same deduplication ID is sent to SQS, response is successful but receiveMessage returns empty array"
  ) {
    val (queueUrl, _) = createFifoQueueWithDLQ(1)

    val firstSendMessageResponse = client.sendMessage(
      new SendMessageRequest(queueUrl, s"Message 1").withMessageDeduplicationId("DedupId").withMessageGroupId("1")
    )
    val m1 = receiveSingleMessageObject(queueUrl)
    val m2 = receiveSingleMessageObject(queueUrl)

    m1 should be(defined)
    m2 should not be defined

    val sendMessageWithSameDedupIdResponse = client.sendMessage(
      new SendMessageRequest(queueUrl, s"Message 3").withMessageDeduplicationId("DedupId").withMessageGroupId("1")
    )

    val m3 = receiveSingleMessageObject(queueUrl)
    m3 should not be defined

    firstSendMessageResponse.getMessageId shouldBe sendMessageWithSameDedupIdResponse.getMessageId
  }

  test(
    "Message with deduplication ID is moved to DLQ (max receive count exceeded) and in DLQ its deduplication ID is changed to Message ID"
  ) {
    val (queueUrl, dlqUrl) = createFifoQueueWithDLQ(2)

    val firstSendMessageResponse = client.sendMessage(
      new SendMessageRequest(queueUrl, "Message 1").withMessageDeduplicationId("DedupId").withMessageGroupId("1")
    )

    getOneMessage(queueUrl)
    getOneMessage(queueUrl)
    getOneMessage(queueUrl)

    val dlqMessage = getOneMessage(dlqUrl)
    dlqMessage.getBody shouldBe "Message 1"
    dlqMessage.getMessageId shouldBe firstSendMessageResponse.getMessageId
    dlqMessage.getAttributes.get("MessageDeduplicationId") shouldBe firstSendMessageResponse.getMessageId
  }

  test(
    "Message is moved to DLQ. Another message with same deduplication ID as the first one had is sent directly do DLQ and both of them are available"
  ) {
    val (queueUrl, dlqUrl) = createFifoQueueWithDLQ(2)

    client.sendMessage(
      new SendMessageRequest(queueUrl, "Message 1").withMessageDeduplicationId("DedupId").withMessageGroupId("1")
    )

    getOneMessage(queueUrl)
    getOneMessage(queueUrl)
    getOneMessage(queueUrl)

    client.sendMessage(
      new SendMessageRequest(dlqUrl, "Message 3").withMessageDeduplicationId("DedupId").withMessageGroupId("1")
    )

    val dlqMessages = client
      .receiveMessage(new ReceiveMessageRequest(dlqUrl).withAttributeNames("All").withMaxNumberOfMessages(10))
      .getMessages
      .asScala
    dlqMessages should have size 2

    val firstDlqMessage = dlqMessages.find(_.getBody == "Message 1").value
    firstDlqMessage.getAttributes.get("MessageDeduplicationId") shouldBe firstDlqMessage.getMessageId
    val secondDlqMessage = dlqMessages.find(_.getBody == "Message 3").value
    secondDlqMessage.getAttributes.get("MessageDeduplicationId") shouldBe "DedupId"
  }

  private def createFifoQueue(suffix: Int = 1, attributes: Map[String, String] = Map.empty): String = {
    val createRequest1 = new CreateQueueRequest(s"testFifoQueue$suffix.fifo")
      .addAttributesEntry("FifoQueue", "true")
      .addAttributesEntry("ContentBasedDeduplication", "true")
    val createRequest2 = attributes.foldLeft(createRequest1) { case (acc, (k, v)) => acc.addAttributesEntry(k, v) }
    client.createQueue(createRequest2).getQueueUrl
  }

  private def createFifoQueueWithDLQ(maxReceiveCount: Int): (String, String) = {
    val dlq = client
      .createQueue(
        new CreateQueueRequest("dlq.fifo")
          .addAttributesEntry("FifoQueue", "true")
          .addAttributesEntry("ContentBasedDeduplication", "true")
          .addAttributesEntry("VisibilityTimeout", "0")
      )
      .getQueueUrl

    val redrivePolicy = RedrivePolicy("dlq.fifo", awsRegion, awsAccountId, maxReceiveCount).toJson.toString()
    val queueUrl =
      client
        .createQueue(
          new CreateQueueRequest("queue.fifo").withAttributes(
            Map(
              "RedrivePolicy" -> redrivePolicy,
              "FifoQueue" -> "true",
              "ContentBasedDeduplication" -> "true",
              "VisibilityTimeout" -> "0"
            ).asJava
          )
        )
        .getQueueUrl
    (queueUrl, dlq)
  }

  private def getOneMessage(queueUrl: String) = {
    client
      .receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(1).withAttributeNames("All"))
      .getMessages
      .asScala
      .headOption
      .orNull
  }

}
