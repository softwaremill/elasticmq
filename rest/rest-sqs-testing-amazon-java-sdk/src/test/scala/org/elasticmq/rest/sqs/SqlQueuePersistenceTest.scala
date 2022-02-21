package org.elasticmq.rest.sqs

import com.amazonaws.services.sqs.model.{
  CreateQueueRequest,
  GetQueueAttributesRequest,
  ReceiveMessageRequest,
  SendMessageRequest
}
import org.elasticmq.actor.reply._
import org.elasticmq.persistence.sql.GetAllMessages
import org.elasticmq.rest.sqs.model.RedrivePolicy
import org.elasticmq.util.Logging
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.elasticmq.rest.sqs.model.RedrivePolicyJson.format
import spray.json.enrichAny

import scala.collection.JavaConverters._

class SqlQueuePersistenceTest
    extends AnyFunSuite
    with SqlQueuePersistenceServer
    with BeforeAndAfter
    with Matchers
    with Logging {

  val testQueueName = "testQueue1"
  val deadLetterQueueName = "testDLQ1"

  val awsAccountId = "123456789012"
  val awsRegion = "elasticmq"

  test("should persist the messages and after restart read the messages") {
    startServerAndRun(pruneDataOnInit = true) {
      val queueUrl = client.createQueue(new CreateQueueRequest(testQueueName)).getQueueUrl

      client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))
      client.sendMessage(new SendMessageRequest(queueUrl, "Message 2"))
      client.sendMessage(new SendMessageRequest(queueUrl, "Message 3"))

      val storedMessages = (store ? GetAllMessages("testQueue1")).futureValue
      storedMessages.map(_.content).toSet shouldBe Set("Message 1", "Message 2", "Message 3")
    }

    startServerAndRun(pruneDataOnInit = false) {
      val queueUrl = client.getQueueUrl(testQueueName).getQueueUrl

      val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(3)).getMessages

      val bodies = messages.asScala.map(_.getBody).toSet
      bodies should be(Set("Message 1", "Message 2", "Message 3"))
    }
  }

  test("should persist, read messages and after restart re-read the messages") {
    startServerAndRun(pruneDataOnInit = true) {
      val queueUrl = client
        .createQueue(
          new CreateQueueRequest(testQueueName)
            .withAttributes(Map("VisibilityTimeout" -> "1").asJava)
        )
        .getQueueUrl

      client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))
      client.sendMessage(new SendMessageRequest(queueUrl, "Message 2"))
      client.sendMessage(new SendMessageRequest(queueUrl, "Message 3"))

      val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(3)).getMessages

      val bodies = messages.asScala.map(_.getBody).toSet
      bodies should be(Set("Message 1", "Message 2", "Message 3"))
    }

    Thread.sleep(1000)

    startServerAndRun(pruneDataOnInit = false) {
      val queueUrl = client.getQueueUrl(testQueueName).getQueueUrl

      val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(3)).getMessages

      val bodies = messages.asScala.map(_.getBody).toSet
      bodies should be(Set("Message 1", "Message 2", "Message 3"))
    }
  }

  test("should persist, read and delete messages and after restart re-read the messages") {
    startServerAndRun(pruneDataOnInit = true) {
      val queueUrl = client
        .createQueue(
          new CreateQueueRequest(testQueueName)
            .withAttributes(Map("VisibilityTimeout" -> "1").asJava)
        )
        .getQueueUrl

      client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))
      client.sendMessage(new SendMessageRequest(queueUrl, "Message 2"))
      client.sendMessage(new SendMessageRequest(queueUrl, "Message 3"))

      val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(3)).getMessages

      messages.forEach(message => client.deleteMessage(queueUrl, message.getReceiptHandle))

      val storedMessages = (store ? GetAllMessages("testQueue1")).futureValue
      storedMessages.toSet shouldBe Set()
    }

    Thread.sleep(1000)

    startServerAndRun(pruneDataOnInit = false) {
      val queueUrl = client.getQueueUrl(testQueueName).getQueueUrl

      val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(3)).getMessages

      val bodies = messages.asScala.map(_.getBody).toSet
      bodies should be(Set())
    }
  }

  test("should persist, read messages, update visibility and after restart re-read the messages") {
    startServerAndRun(pruneDataOnInit = true) {
      val queueUrl = client
        .createQueue(
          new CreateQueueRequest(testQueueName)
            .withAttributes(Map("VisibilityTimeout" -> "1").asJava)
        )
        .getQueueUrl

      client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))
      client.sendMessage(new SendMessageRequest(queueUrl, "Message 2"))
      client.sendMessage(new SendMessageRequest(queueUrl, "Message 3"))

      val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(3)).getMessages

      messages.forEach(message => client.changeMessageVisibility(queueUrl, message.getReceiptHandle, 2))
    }

    Thread.sleep(1000)

    startServerAndRun(pruneDataOnInit = false) {
      val queueUrl = client.getQueueUrl(testQueueName).getQueueUrl

      val messages1 = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(3)).getMessages

      val bodies1 = messages1.asScala.map(_.getBody).toSet
      bodies1 should be(Set())

      Thread.sleep(1000)

      val messages2 = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(3)).getMessages

      val bodies2 = messages2.asScala.map(_.getBody).toSet
      bodies2 should be(Set("Message 1", "Message 2", "Message 3"))
    }
  }

  test("should move messages to DLQ and restore") {
    startServerAndRun(pruneDataOnInit = true) {
      val dlQueueUrl = client
        .createQueue(
          new CreateQueueRequest(deadLetterQueueName)
        )
        .getQueueUrl

      val queueUrl = client
        .createQueue(
          new CreateQueueRequest(testQueueName)
            .withAttributes(
              Map(
                "VisibilityTimeout" -> "1",
                "RedrivePolicy" -> RedrivePolicy(deadLetterQueueName, awsAccountId, awsRegion, 1).toJson.toString
              ).asJava
            )
        )
        .getQueueUrl

      client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))
      client.sendMessage(new SendMessageRequest(queueUrl, "Message 2"))
      client.sendMessage(new SendMessageRequest(queueUrl, "Message 3"))

      val messages1 = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(3)).getMessages
      messages1 should have size 3

      Thread.sleep(1000)

      val messages2 = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(3)).getMessages
      messages2 shouldBe empty

      val attributes =
        client
          .getQueueAttributes(new GetQueueAttributesRequest(queueUrl).withAttributeNames("ApproximateNumberOfMessages"))
          .getAttributes
          .asScala

      attributes.get("ApproximateNumberOfMessages") shouldBe Some("0")

      val dlqAttributes =
        client
          .getQueueAttributes(
            new GetQueueAttributesRequest(dlQueueUrl).withAttributeNames("ApproximateNumberOfMessages")
          )
          .getAttributes
          .asScala

      dlqAttributes.get("ApproximateNumberOfMessages") shouldBe Some("3")
    }

    Thread.sleep(1000)

    startServerAndRun(pruneDataOnInit = false) {
      val queueUrl = client.getQueueUrl(testQueueName).getQueueUrl
      val dlQueueUrl = client.getQueueUrl(deadLetterQueueName).getQueueUrl

      val attributes =
        client
          .getQueueAttributes(new GetQueueAttributesRequest(queueUrl).withAttributeNames("ApproximateNumberOfMessages"))
          .getAttributes
          .asScala

      attributes.get("ApproximateNumberOfMessages") shouldBe Some("0")

      val dlqAttributes =
        client
          .getQueueAttributes(
            new GetQueueAttributesRequest(dlQueueUrl).withAttributeNames("ApproximateNumberOfMessages")
          )
          .getAttributes
          .asScala

      dlqAttributes.get("ApproximateNumberOfMessages") shouldBe Some("3")

      val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(3)).getMessages
      messages shouldBe empty

      val dlqMessages =
        client.receiveMessage(new ReceiveMessageRequest(dlQueueUrl).withMaxNumberOfMessages(3)).getMessages

      val bodies = dlqMessages.asScala.map(_.getBody).toSet
      bodies shouldBe Set("Message 1", "Message 2", "Message 3")
    }
  }
}
