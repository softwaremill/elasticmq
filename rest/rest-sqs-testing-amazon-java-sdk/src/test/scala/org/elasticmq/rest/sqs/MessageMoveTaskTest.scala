package org.elasticmq.rest.sqs

import com.amazonaws.services.sqs.model.QueueAttributeName.ApproximateNumberOfMessages
import com.amazonaws.services.sqs.model.{CancelMessageMoveTaskRequest => AWSCancelMessageMoveTaskRequest, ListMessageMoveTasksRequest => AWSListMessageMoveTasksRequest, _}
import org.elasticmq.rest.sqs.model.RedrivePolicy
import org.elasticmq.rest.sqs.model.RedrivePolicyJson.format
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import spray.json.enrichAny

import java.util.UUID
import scala.concurrent.duration.DurationInt

class MessageMoveTaskTest extends SqsClientServerCommunication with Matchers with Eventually {

  private val NumMessages = 6
  private val DlqArn = s"arn:aws:sqs:$awsRegion:$awsAccountId:testQueue-dlq"

  test("should run message move task") {
    // given
    val (queue, dlq) = createQueuesAndPopulateDlq()

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
    val (queue, dlq) = createQueuesAndPopulateDlq()

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

  test("should not run two message move tasks in parallel") {
    // given
    val (queue, dlq) = createQueuesAndPopulateDlq()

    // when: start message move task
    client.startMessageMoveTask(
      new StartMessageMoveTaskRequest()
        .withSourceArn(DlqArn)
        .withMaxNumberOfMessagesPerSecond(1)
    )

    // and: try to start another message move task
    val thrown = intercept[UnsupportedOperationException] {
      client.startMessageMoveTask(
        new StartMessageMoveTaskRequest()
          .withSourceArn(DlqArn)
          .withMaxNumberOfMessagesPerSecond(1)
      )
    }

    // then
    thrown.getErrorMessage shouldBe "A message move task is already running on queue \"testQueue-dlq\""

    // and: ensure that not all messages were moved back to the original queue after 2 seconds
    Thread.sleep(2000)
    fetchApproximateNumberOfMessages(queue.getQueueUrl) should (be > 1 and be < 6)
    fetchApproximateNumberOfMessages(dlq.getQueueUrl) should (be > 1 and be < 6)
  }

  test("should run message move task and list it") {
    // given
    val (queue, dlq) = createQueuesAndPopulateDlq()

    // when
    val taskHandle = client
      .startMessageMoveTask(
        new StartMessageMoveTaskRequest()
          .withSourceArn(DlqArn)
          .withMaxNumberOfMessagesPerSecond(1)
      )
      .getTaskHandle

    // and
    val results =
      client
        .listMessageMoveTasks(new AWSListMessageMoveTasksRequest().withSourceArn(DlqArn).withMaxResults(10))
        .getResults

    // then
    results.size shouldEqual 1
    results.get(0).getTaskHandle shouldBe taskHandle
    results.get(0).getStatus shouldBe "RUNNING"
  }

  test("should run two message move task and list them in the correct order") {
    // given
    val (queue, dlq) = createQueuesAndPopulateDlq()

    // when
    val firstTaskHandle = client
      .startMessageMoveTask(
        new StartMessageMoveTaskRequest()
          .withSourceArn(DlqArn)
      )
      .getTaskHandle

    // and
    receiveAllMessagesTwice(queue)

    // and
    val secondTaskHandle = client
      .startMessageMoveTask(
        new StartMessageMoveTaskRequest()
          .withSourceArn(DlqArn)
          .withMaxNumberOfMessagesPerSecond(1)
      )
      .getTaskHandle

    // and
    val results =
      client
        .listMessageMoveTasks(new AWSListMessageMoveTasksRequest().withSourceArn(DlqArn).withMaxResults(10))
        .getResults

    // then
    results.size shouldEqual 2
    results.get(0).getTaskHandle shouldBe secondTaskHandle
    results.get(0).getStatus shouldBe "RUNNING"
    results.get(1).getTaskHandle shouldBe firstTaskHandle
    results.get(1).getStatus shouldBe "COMPLETED"
  }

  test("should fail to list tasks for non-existing source ARN") {
    intercept[QueueDoesNotExistException] {
      client.listMessageMoveTasks(
        new AWSListMessageMoveTasksRequest()
          .withSourceArn(s"arn:aws:sqs:$awsRegion:$awsAccountId:nonExistingQueue")
          .withMaxResults(10)
      )
    }
  }

  test("should fail to list tasks for invalid ARN") {
    intercept[QueueDoesNotExistException] {
      client.listMessageMoveTasks(
        new AWSListMessageMoveTasksRequest()
          .withSourceArn("invalidArn")
          .withMaxResults(10)
      )
    }
  }

  test("should run and cancel message move task") {
    // given
    val (queue, dlq) = createQueuesAndPopulateDlq()

    // when: start message move task
    val taskHandle = client
      .startMessageMoveTask(
        new StartMessageMoveTaskRequest()
          .withSourceArn(DlqArn)
          .withMaxNumberOfMessagesPerSecond(1)
      )
      .getTaskHandle

    // and: cancel the task after 2 seconds
    Thread.sleep(2000)
    val numMessagesMoved = client
      .cancelMessageMoveTask(new AWSCancelMessageMoveTaskRequest().withTaskHandle(taskHandle))
      .getApproximateNumberOfMessagesMoved

    // and: fetch ApproximateNumberOfMessages
    val numMessagesInMainQueue = fetchApproximateNumberOfMessages(queue.getQueueUrl)
    val numMessagesInDlQueue = fetchApproximateNumberOfMessages(dlq.getQueueUrl)

    // then
    numMessagesMoved shouldEqual numMessagesInMainQueue

    // then: ApproximateNumberOfMessages should not change after 2 seconds
    Thread.sleep(2000)
    fetchApproximateNumberOfMessages(queue.getQueueUrl) shouldEqual numMessagesInMainQueue
    fetchApproximateNumberOfMessages(dlq.getQueueUrl) shouldEqual numMessagesInDlQueue
  }

  test("should fail to cancel non-existing task") {
    intercept[ResourceNotFoundException] {
      client
        .cancelMessageMoveTask(new AWSCancelMessageMoveTaskRequest().withTaskHandle(UUID.randomUUID().toString))
        .getApproximateNumberOfMessagesMoved
    }
  }

  private def createQueuesAndPopulateDlq(): (CreateQueueResult, CreateQueueResult) = {
    val dlq = client.createQueue(new CreateQueueRequest("testQueue-dlq"))
    val redrivePolicy = RedrivePolicy("testQueue-dlq", awsRegion, awsAccountId, 1).toJson.compactPrint
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

    // and: receive messages twice to make them move to DLQ
    receiveAllMessagesTwice(queue)

    // then: ensure that messages are in DLQ
    eventually(timeout(2.seconds), interval(100.millis)) {
      fetchApproximateNumberOfMessages(queue.getQueueUrl) shouldEqual 0
      fetchApproximateNumberOfMessages(dlq.getQueueUrl) shouldEqual NumMessages
    }

    (queue, dlq)
  }

  private def receiveAllMessagesTwice(queue: CreateQueueResult): Unit = {
    for (i <- 0 until NumMessages) {
      client.receiveMessage(queue.getQueueUrl)
    }

    Thread.sleep(1500)
    for (i <- 0 until NumMessages) {
      client.receiveMessage(queue.getQueueUrl)
    }
  }

  private def fetchApproximateNumberOfMessages(queueUrl: String): Int = {
    client
      .getQueueAttributes(
        new GetQueueAttributesRequest().withQueueUrl(queueUrl).withAttributeNames(ApproximateNumberOfMessages)
      )
      .getAttributes
      .get(ApproximateNumberOfMessages.toString)
      .toInt
  }
}
