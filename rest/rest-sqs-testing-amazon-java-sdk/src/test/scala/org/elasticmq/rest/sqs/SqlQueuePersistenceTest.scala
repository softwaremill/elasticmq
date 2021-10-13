package org.elasticmq.rest.sqs

import com.amazonaws.services.sqs.model.{CreateQueueRequest, ReceiveMessageRequest, SendMessageRequest}
import org.elasticmq.actor.reply._
import org.elasticmq.persistence.sql.GetAllMessages
import org.elasticmq.util.Logging
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.collection.JavaConverters._
import scala.concurrent.Await

class SqlQueuePersistenceTest extends AnyFunSuite with SqlQueuePersistenceServer with BeforeAndAfter with Matchers with Logging {

  val testQueueName = "testQueue1"

  test("should persist the messages and after restart read the messages") {
    startServerAndRun(pruneDataOnInit = true) {
      val queueUrl = client.createQueue(new CreateQueueRequest(testQueueName)).getQueueUrl

      client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))
      client.sendMessage(new SendMessageRequest(queueUrl, "Message 2"))
      client.sendMessage(new SendMessageRequest(queueUrl, "Message 3"))

      val storedMessages = Await.result(store ? GetAllMessages("testQueue1"), timeout.duration)
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
      val queueUrl = client.createQueue(new CreateQueueRequest(testQueueName)
        .withAttributes(Map("VisibilityTimeout" -> "1").asJava)).getQueueUrl

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
      val queueUrl = client.createQueue(new CreateQueueRequest(testQueueName)
        .withAttributes(Map("VisibilityTimeout" -> "1").asJava)).getQueueUrl

      client.sendMessage(new SendMessageRequest(queueUrl, "Message 1"))
      client.sendMessage(new SendMessageRequest(queueUrl, "Message 2"))
      client.sendMessage(new SendMessageRequest(queueUrl, "Message 3"))

      val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(3)).getMessages

      messages.forEach(message => client.deleteMessage(queueUrl, message.getReceiptHandle))

      val storedMessages = Await.result(store ? GetAllMessages("testQueue1"), timeout.duration)
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
      val queueUrl = client.createQueue(new CreateQueueRequest(testQueueName)
        .withAttributes(Map("VisibilityTimeout" -> "1").asJava)).getQueueUrl

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
}
