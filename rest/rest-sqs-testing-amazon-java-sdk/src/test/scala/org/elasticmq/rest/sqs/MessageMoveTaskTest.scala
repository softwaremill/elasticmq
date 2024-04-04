package org.elasticmq.rest.sqs

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.QueueAttributeName.ApproximateNumberOfMessages
import com.amazonaws.services.sqs.model._
import org.apache.http.HttpHost
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.message.BasicNameValuePair
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.elasticmq._
import org.elasticmq.rest.sqs.model.RedrivePolicy
import org.elasticmq.rest.sqs.model.RedrivePolicyJson.format
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import spray.json.enrichAny

import java.net.URI
import java.nio.ByteBuffer
import java.util.UUID
import scala.collection.JavaConverters._
import scala.concurrent.duration.DurationInt
import scala.io.Source
import scala.util.control.Exception._

class MessageMoveTaskTest extends SqsClientServerCommunication with Matchers with Eventually {

  test("should run message move task") {
    // given
    val dlq = client.createQueue(new CreateQueueRequest("testQueue-dlq"))
    val redrivePolicy = RedrivePolicy("testQueue-dlq", awsRegion, awsAccountId, 1).toJson.toString()
    val queue =
      client.createQueue(
        new CreateQueueRequest("testQueue")
          .addAttributesEntry(redrivePolicyAttribute, redrivePolicy)
          .addAttributesEntry("VisibilityTimeout", "1")
      )

    // when: send messages
    val numMessages = 6
    for (i <- 0 until numMessages) {
      client.sendMessage(queue.getQueueUrl, "Test message " + i)
    }

    // and: receive messages
    for (i <- 0 until numMessages) {
      client.receiveMessage(queue.getQueueUrl)
    }

    // and: receive messages again to make them move to DLQ
    Thread.sleep(1500)
    for (i <- 0 until numMessages) {
      client.receiveMessage(queue.getQueueUrl)
    }

    // then: ensure that messages are in DLQ
    eventually(timeout(2.seconds), interval(100.millis)) {
      fetchApproximateNumberOfMessages(queue.getQueueUrl) shouldEqual 0
      fetchApproximateNumberOfMessages(dlq.getQueueUrl) shouldEqual numMessages
    }

    // when: start message move task
    client.startMessageMoveTask(
      new StartMessageMoveTaskRequest().withSourceArn(s"arn:aws:sqs:$awsRegion:$awsAccountId:testQueue-dlq")
    )

    // then: ensure that messages are moved back to the original queue
    eventually(timeout(5.seconds), interval(100.millis)) {
      fetchApproximateNumberOfMessages(queue.getQueueUrl) shouldEqual numMessages
      fetchApproximateNumberOfMessages(dlq.getQueueUrl) shouldEqual 0
    }
  }

  test("should run message move task with max number of messages per second") {
    // given
    val dlq = client.createQueue(new CreateQueueRequest("testQueue-dlq"))
    val redrivePolicy = RedrivePolicy("testQueue-dlq", awsRegion, awsAccountId, 1).toJson.toString()
    val queue =
      client.createQueue(
        new CreateQueueRequest("testQueue")
          .addAttributesEntry(redrivePolicyAttribute, redrivePolicy)
          .addAttributesEntry("VisibilityTimeout", "1")
      )

    // when: send messages
    val numMessages = 6
    for (i <- 0 until numMessages) {
      client.sendMessage(queue.getQueueUrl, "Test message " + i)
    }

    // and: receive messages
    for (i <- 0 until numMessages) {
      client.receiveMessage(queue.getQueueUrl)
    }

    // and: receive messages again to make them move to DLQ
    Thread.sleep(1500)
    for (i <- 0 until numMessages) {
      client.receiveMessage(queue.getQueueUrl)
    }

    // then: ensure that messages are in DLQ
    eventually(timeout(2.seconds), interval(100.millis)) {
      fetchApproximateNumberOfMessages(queue.getQueueUrl) shouldEqual 0
      fetchApproximateNumberOfMessages(dlq.getQueueUrl) shouldEqual numMessages
    }

    // when: start message move task
    client.startMessageMoveTask(
      new StartMessageMoveTaskRequest()
        .withSourceArn(s"arn:aws:sqs:$awsRegion:$awsAccountId:testQueue-dlq")
        .withMaxNumberOfMessagesPerSecond(1)
    )

    // then: ensure that not all messages were moved back to the original queue after 2 seconds
    Thread.sleep(2000)
    fetchApproximateNumberOfMessages(queue.getQueueUrl) should (be > 1 and be < 6)
    fetchApproximateNumberOfMessages(dlq.getQueueUrl) should (be > 1 and be < 6)
  }

  test("should run and cancel message move task") {
    // given
    val dlq = client.createQueue(new CreateQueueRequest("testQueue-dlq"))
    val redrivePolicy = RedrivePolicy("testQueue-dlq", awsRegion, awsAccountId, 1).toJson.toString()
    val queue =
      client.createQueue(
        new CreateQueueRequest("testQueue")
          .addAttributesEntry(redrivePolicyAttribute, redrivePolicy)
          .addAttributesEntry("VisibilityTimeout", "1")
      )

    // when: send messages
    val numMessages = 6
    for (i <- 0 until numMessages) {
      client.sendMessage(queue.getQueueUrl, "Test message " + i)
    }

    // and: receive messages
    for (i <- 0 until numMessages) {
      client.receiveMessage(queue.getQueueUrl)
    }

    // and: receive messages again to make them move to DLQ
    Thread.sleep(1500)
    for (i <- 0 until numMessages) {
      client.receiveMessage(queue.getQueueUrl)
    }

    // then: ensure that messages are in DLQ
    eventually(timeout(2.seconds), interval(100.millis)) {
      fetchApproximateNumberOfMessages(queue.getQueueUrl) shouldEqual 0
      fetchApproximateNumberOfMessages(dlq.getQueueUrl) shouldEqual numMessages
    }

    // when: start message move task
    val taskHandle = client.startMessageMoveTask(
      new StartMessageMoveTaskRequest()
        .withSourceArn(s"arn:aws:sqs:$awsRegion:$awsAccountId:testQueue-dlq")
        .withMaxNumberOfMessagesPerSecond(1)
    ).getTaskHandle

    // and: cancel the task after 2 seconds
    Thread.sleep(2000)
    client.cancelMessageMoveTask(new CancelMessageMoveTaskRequest().withTaskHandle(taskHandle))

    // and: fetch ApproximateNumberOfMessages
    val numMessagesInMainQueue = fetchApproximateNumberOfMessages(queue.getQueueUrl)
    val numMessagesInDlQueue = fetchApproximateNumberOfMessages(dlq.getQueueUrl)

    // then: ApproximateNumberOfMessages should not change after 2 seconds
    Thread.sleep(2000)
    fetchApproximateNumberOfMessages(queue.getQueueUrl) shouldEqual numMessagesInMainQueue
    fetchApproximateNumberOfMessages(dlq.getQueueUrl) shouldEqual numMessagesInDlQueue
  }

  private def fetchApproximateNumberOfMessages(queueUrl: String): Int = {
    client
      .getQueueAttributes(
        new GetQueueAttributesRequest().withQueueUrl(queueUrl).withAttributeNames(ApproximateNumberOfMessages)
      )
      .getAttributes
      .get(ApproximateNumberOfMessages.toString).toInt
  }
}
