package org.elasticmq.rest.sqs

import com.amazonaws.services.sqs.model.QueueAttributeName.ApproximateNumberOfMessages
import com.amazonaws.services.sqs.model._
import org.elasticmq.rest.sqs.model.RedrivePolicy
import org.elasticmq.rest.sqs.model.RedrivePolicyJson.format
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import spray.json.enrichAny

import scala.concurrent.duration.DurationInt

class MessageMoveTaskTest extends SqsClientServerCommunication with Matchers with Eventually {

  private val NumMessages = 6
  private val DlqArn = s"arn:aws:sqs:$awsRegion:$awsAccountId:testQueue-dlq"

  test("should run message move task") {
    // given
    val (queue, dlq) = createQueuesAndPopulateDlq

    // when: start message move task
    client.startMessageMoveTask(
      new StartMessageMoveTaskRequest().withSourceArn(DlqArn)
    )

    // then: ensure that messages are moved back to the original queue
    eventually(timeout(5.seconds), interval(100.millis)) {
      fetchApproximateNumberOfMessages(queue.getQueueUrl) shouldEqual NumMessages
      fetchApproximateNumberOfMessages(dlq.getQueueUrl) shouldEqual 0
    }
  }

  test("should run message move task with max number of messages per second") {
    // given
    val (queue, dlq) = createQueuesAndPopulateDlq

    // when: start message move task
    client.startMessageMoveTask(
      new StartMessageMoveTaskRequest()
        .withSourceArn(DlqArn)
        .withMaxNumberOfMessagesPerSecond(1)
    )

    // then: ensure that not all messages were moved back to the original queue after 2 seconds
    Thread.sleep(2000)
    fetchApproximateNumberOfMessages(queue.getQueueUrl) should (be > 1 and be < 6)
    fetchApproximateNumberOfMessages(dlq.getQueueUrl) should (be > 1 and be < 6)
  }

  test("should run and cancel message move task") {
    // given
    val (queue, dlq) = createQueuesAndPopulateDlq

    // when: start message move task
    val taskHandle = client.startMessageMoveTask(
      new StartMessageMoveTaskRequest()
        .withSourceArn(DlqArn)
        .withMaxNumberOfMessagesPerSecond(1)
    ).getTaskHandle

    // and: cancel the task after 2 seconds
    Thread.sleep(2000)
    val numMessagesMoved = client.cancelMessageMoveTask(new CancelMessageMoveTaskRequest().withTaskHandle(taskHandle))
      .getApproximateNumberOfMessagesMoved

    // and: fetch ApproximateNumberOfMessages
    val numMessagesInMainQueue = fetchApproximateNumberOfMessages(queue.getQueueUrl)
    val numMessagesInDlQueue = fetchApproximateNumberOfMessages(dlq.getQueueUrl)

    numMessagesMoved shouldEqual numMessagesInMainQueue

    // then: ApproximateNumberOfMessages should not change after 2 seconds
    Thread.sleep(2000)
    fetchApproximateNumberOfMessages(queue.getQueueUrl) shouldEqual numMessagesInMainQueue
    fetchApproximateNumberOfMessages(dlq.getQueueUrl) shouldEqual numMessagesInDlQueue
  }

  private def createQueuesAndPopulateDlq: (CreateQueueResult, CreateQueueResult) = {
    val dlq = client.createQueue(new CreateQueueRequest("testQueue-dlq"))
    val redrivePolicy = RedrivePolicy("testQueue-dlq", awsRegion, awsAccountId, 1).toJson.toString()
    val queue =
      client.createQueue(
        new CreateQueueRequest("testQueue")
          .addAttributesEntry(redrivePolicyAttribute, redrivePolicy)
          .addAttributesEntry("VisibilityTimeout", "1")
      )

    // when: send messages
    for (i <- 0 until NumMessages) {
      client.sendMessage(queue.getQueueUrl, "Test message " + i)
    }

    // and: receive messages
    for (i <- 0 until NumMessages) {
      client.receiveMessage(queue.getQueueUrl)
    }

    // and: receive messages again to make them move to DLQ
    Thread.sleep(1500)
    for (i <- 0 until NumMessages) {
      client.receiveMessage(queue.getQueueUrl)
    }

    // then: ensure that messages are in DLQ
    eventually(timeout(2.seconds), interval(100.millis)) {
      fetchApproximateNumberOfMessages(queue.getQueueUrl) shouldEqual 0
      fetchApproximateNumberOfMessages(dlq.getQueueUrl) shouldEqual NumMessages
    }

    (queue, dlq)
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
