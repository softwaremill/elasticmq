package org.elasticmq.rest.sqs.aws

import org.elasticmq.actor.reply._
import org.elasticmq.persistence.sql.GetAllMessages
import org.elasticmq.rest.sqs.SqlQueuePersistenceServer
import org.elasticmq.rest.sqs.client._
import org.elasticmq.rest.sqs.model.RedrivePolicy
import org.elasticmq.rest.sqs.model.RedrivePolicyJson.format
import org.elasticmq.util.Logging
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import spray.json.enrichAny

class SqlQueuePersistenceTests
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
      val queueUrl = testClient.createQueue(testQueueName).toOption.get

      testClient.sendMessage(queueUrl, "Message 1")
      testClient.sendMessage(queueUrl, "Message 2")
      testClient.sendMessage(queueUrl, "Message 3")

      val storedMessages = (store ? GetAllMessages(testQueueName)).futureValue
      storedMessages.map(_.content.value).toSet shouldBe Set("Message 1", "Message 2", "Message 3")
    }

    startServerAndRun(pruneDataOnInit = false) {
      val queueUrl = testClient.getQueueUrl(testQueueName).toOption.get

      val messages = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))

      val bodies = messages.map(_.body).toSet
      bodies should be(Set("Message 1", "Message 2", "Message 3"))
    }
  }

  test("should persist, read messages and after restart re-read the messages") {
    startServerAndRun(pruneDataOnInit = true) {
      val queueUrl = testClient
        .createQueue(
          testQueueName,
          Map(VisibilityTimeoutAttributeName -> "1")
        )
        .toOption
        .get

      testClient.sendMessage(queueUrl, "Message 1")
      testClient.sendMessage(queueUrl, "Message 2")
      testClient.sendMessage(queueUrl, "Message 3")

      val messages = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))

      val bodies = messages.map(_.body).toSet
      bodies should be(Set("Message 1", "Message 2", "Message 3"))
    }

    Thread.sleep(1000)

    startServerAndRun(pruneDataOnInit = false) {
      val queueUrl = testClient.getQueueUrl(testQueueName).toOption.get

      val messages = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))

      val bodies = messages.map(_.body).toSet
      bodies should be(Set("Message 1", "Message 2", "Message 3"))
    }
  }

  test("should persist, read and delete messages and after restart re-read the messages") {
    startServerAndRun(pruneDataOnInit = true) {
      val queueUrl = testClient
        .createQueue(
          testQueueName,
          Map(VisibilityTimeoutAttributeName -> "1")
        )
        .toOption
        .get

      testClient.sendMessage(queueUrl, "Message 1")
      testClient.sendMessage(queueUrl, "Message 2")
      testClient.sendMessage(queueUrl, "Message 3")

      val messages = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))

      messages.foreach(message => testClient.deleteMessage(queueUrl, message.receiptHandle))

      val storedMessages = (store ? GetAllMessages(testQueueName)).futureValue
      storedMessages.toSet shouldBe Set()
    }

    Thread.sleep(1000)

    startServerAndRun(pruneDataOnInit = false) {
      val queueUrl = testClient.getQueueUrl(testQueueName).toOption.get

      val messages = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))

      val bodies = messages.map(_.body).toSet
      bodies should be(Set())
    }
  }

  test("should persist, read messages, update visibility and after restart re-read the messages") {
    startServerAndRun(pruneDataOnInit = true) {
      val queueUrl = testClient
        .createQueue(
          testQueueName,
          Map(VisibilityTimeoutAttributeName -> "1")
        )
        .toOption
        .get

      testClient.sendMessage(queueUrl, "Message 1")
      testClient.sendMessage(queueUrl, "Message 2")
      testClient.sendMessage(queueUrl, "Message 3")

      val messages = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))

      messages.foreach(message => testClient.changeMessageVisibility(queueUrl, message.receiptHandle, 2))
    }

    Thread.sleep(1000)

    startServerAndRun(pruneDataOnInit = false) {
      val queueUrl = testClient.getQueueUrl(testQueueName).toOption.get

      val messages1 = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))

      val bodies1 = messages1.map(_.body).toSet
      bodies1 should be(Set())

      Thread.sleep(1000)

      val messages2 = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))

      val bodies2 = messages2.map(_.body).toSet
      bodies2 should be(Set("Message 1", "Message 2", "Message 3"))
    }
  }

  test("should move messages to DLQ and restore") {
    startServerAndRun(pruneDataOnInit = true) {
      val dlQueueUrl = testClient
        .createQueue(deadLetterQueueName)
        .toOption
        .get

      val queueUrl = testClient
        .createQueue(
          testQueueName,
          Map(
            VisibilityTimeoutAttributeName -> "1",
            RedrivePolicyAttributeName -> RedrivePolicy(deadLetterQueueName, awsAccountId, awsRegion, 1).toJson.toString
          )
        )
        .toOption
        .get

      testClient.sendMessage(queueUrl, "Message 1")
      testClient.sendMessage(queueUrl, "Message 2")
      testClient.sendMessage(queueUrl, "Message 3")

      val messages1 = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))
      messages1 should have size 3

      Thread.sleep(1000)

      val messages2 = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))
      messages2 shouldBe empty

      val attributes = testClient.getQueueAttributes(queueUrl, ApproximateNumberOfMessagesAttributeName)

      attributes.get(ApproximateNumberOfMessagesAttributeName.value) shouldBe Some("0")

      val dlqAttributes = testClient.getQueueAttributes(dlQueueUrl, ApproximateNumberOfMessagesAttributeName)

      dlqAttributes.get(ApproximateNumberOfMessagesAttributeName.value) shouldBe Some("3")
    }

    Thread.sleep(1000)

    startServerAndRun(pruneDataOnInit = false) {
      val queueUrl = testClient.getQueueUrl(testQueueName).toOption.get
      val dlQueueUrl = testClient.getQueueUrl(deadLetterQueueName).toOption.get

      val attributes = testClient.getQueueAttributes(queueUrl, ApproximateNumberOfMessagesAttributeName)

      attributes.get(ApproximateNumberOfMessagesAttributeName.value) shouldBe Some("0")

      val dlqAttributes = testClient.getQueueAttributes(dlQueueUrl, ApproximateNumberOfMessagesAttributeName)

      dlqAttributes.get(ApproximateNumberOfMessagesAttributeName.value) shouldBe Some("3")

      val messages = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(3))
      messages shouldBe empty

      val dlqMessages = testClient.receiveMessage(dlQueueUrl, maxNumberOfMessages = Some(3))

      val bodies = dlqMessages.map(_.body).toSet
      bodies shouldBe Set("Message 1", "Message 2", "Message 3")
    }
  }
}
