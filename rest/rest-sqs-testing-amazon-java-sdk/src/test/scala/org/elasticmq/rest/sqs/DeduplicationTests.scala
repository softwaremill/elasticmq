package org.elasticmq.rest.sqs

import com.amazonaws.services.sqs.model.{CreateQueueRequest, ReceiveMessageRequest, SendMessageRequest}
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters.CollectionHasAsScala

class DeduplicationTests extends SqsClientServerCommunication with Matchers {

  test("FIFO queues should deduplicate messages based on the message body") {
    // Given
    val queueUrl = createFifoQueue()

    // When
    val sentMessages = for (i <- 1 to 10) yield {
      client.sendMessage(new SendMessageRequest(queueUrl, "Message").withMessageGroupId(s"$i"))
    }
    val messages1 =
      client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(4)).getMessages.asScala
    //client.deleteMessage(queueUrl, messages1.head.getReceiptHandle)
    val messages2 =
      client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(4)).getMessages.asScala

    // Then
    sentMessages.map(_.getMessageId).toSet should have size 1
    messages1.map(_.getBody) should have size 1
    messages1.map(_.getBody).toSet should be(Set("Message"))
    messages2 should have size 0
  }

  test("FIFO queues should deduplicate messages based on the message deduplication attribute") {
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

  private def createFifoQueue(suffix: Int = 1, attributes: Map[String, String] = Map.empty): String = {
    val createRequest1 = new CreateQueueRequest(s"testFifoQueue$suffix.fifo")
      .addAttributesEntry("FifoQueue", "true")
      .addAttributesEntry("ContentBasedDeduplication", "true")
    val createRequest2 = attributes.foldLeft(createRequest1) { case (acc, (k, v)) => acc.addAttributesEntry(k, v) }
    client.createQueue(createRequest2).getQueueUrl
  }

}
