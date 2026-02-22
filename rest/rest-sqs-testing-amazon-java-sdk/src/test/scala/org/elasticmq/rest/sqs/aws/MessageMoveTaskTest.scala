package org.elasticmq.rest.sqs.aws

import org.elasticmq.rest.sqs.client._
import org.elasticmq.rest.sqs.model.RedrivePolicy
import org.elasticmq.rest.sqs.model.RedrivePolicyJson.format
import org.elasticmq.rest.sqs.{AwsConfig, SqsClientServerCommunication, SqsClientServerWithSdkV2Communication}
import org.scalatest.concurrent.Eventually
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import spray.json.enrichAny

import java.util.UUID
import scala.concurrent.duration.DurationInt

abstract class MessageMoveTaskTest
    extends AnyFunSuite
    with HasSqsTestClient
    with AwsConfig
    with Matchers
    with Eventually {

  private val NumMessages = 6
  private val DlqArn = s"arn:aws:sqs:$awsRegion:$awsAccountId:testQueue-dlq"

  test("should run message move task") {
    // given
    val (queue, dlq) = createQueuesAndPopulateDlq()

    // when: start message move task
    testClient.startMessageMoveTask(DlqArn)

    // then: ensure that messages are moved back to the original queue
    eventually(timeout(5.seconds), interval(100.millis)) {
      fetchApproximateNumberOfMessages(queue) shouldEqual NumMessages
      fetchApproximateNumberOfMessages(dlq) shouldEqual 0
    }
  }

  test("should run message move task with max number of messages per second") {
    // given
    val (queue, dlq) = createQueuesAndPopulateDlq()

    // when: start message move task
    testClient.startMessageMoveTask(DlqArn, maxNumberOfMessagesPerSecond = Some(1))

    // then: ensure that not all messages were moved back to the original queue after 2 seconds
    eventually(timeout(5.seconds), interval(100.millis)) {
      fetchApproximateNumberOfMessages(queue) should (be > 1 and be < 6)
      fetchApproximateNumberOfMessages(dlq) should (be > 1 and be < 6)
    }
  }

  test("should not run two message move tasks in parallel") {
    // given
    val (queue, dlq) = createQueuesAndPopulateDlq()

    // when: start message move task
    testClient.startMessageMoveTask(DlqArn, maxNumberOfMessagesPerSecond = Some(1))

    // and: try to start another message move task
    val result = testClient.startMessageMoveTask(DlqArn)

    // then
    result shouldBe Left(
      SqsClientError(
        UnsupportedOperation,
        "A message move task is already running on queue \"testQueue-dlq\""
      )
    )

    // and: ensure that not all messages were moved back to the original queue after 2 seconds
    eventually(timeout(5.seconds), interval(100.millis)) {
      fetchApproximateNumberOfMessages(queue) should (be > 1 and be < 6)
      fetchApproximateNumberOfMessages(dlq) should (be > 1 and be < 6)
    }
  }

  test("should run message move task and list it") {
    // given
    val (queue, dlq) = createQueuesAndPopulateDlq()

    // when
    val taskHandle = testClient.startMessageMoveTask(DlqArn, maxNumberOfMessagesPerSecond = Some(1)).right.get

    // and
    val results = testClient.listMessageMoveTasks(DlqArn, maxResults = Some(10)).right.get

    // then
    results.size shouldEqual 1
    results.head.taskHandle shouldBe taskHandle
    results.head.status shouldBe "RUNNING"
  }

  test("should run two message move task and list them in the correct order") {
    // given
    val (queue, dlq) = createQueuesAndPopulateDlq()

    // when
    val firstTaskHandle = testClient.startMessageMoveTask(DlqArn).right.get

    // and
    receiveAllMessagesTwice(queue)

    // and
    val secondTaskHandle = testClient.startMessageMoveTask(DlqArn, maxNumberOfMessagesPerSecond = Some(1)).right.get

    // and
    val results = testClient.listMessageMoveTasks(DlqArn, maxResults = Some(10)).right.get

    // then
    results.size shouldEqual 2
    results(0).taskHandle shouldBe secondTaskHandle
    results(0).status shouldBe "RUNNING"
    results(1).taskHandle shouldBe firstTaskHandle
    results(1).status shouldBe "COMPLETED"
  }

  test("should fail to list tasks for non-existing source ARN") {
    testClient.listMessageMoveTasks(s"arn:aws:sqs:$awsRegion:$awsAccountId:nonExistingQueue") shouldBe Left(
      SqsClientError(QueueDoesNotExist, "The specified queue does not exist.")
    )
  }

  test("should fail to list tasks for invalid ARN") {
    testClient.listMessageMoveTasks(s"invalidArn") shouldBe Left(
      SqsClientError(QueueDoesNotExist, "The specified queue does not exist.")
    )
  }

  test("should run and cancel message move task") {
    // given
    val (queue, dlq) = createQueuesAndPopulateDlq()

    // when: start message move task
    val taskHandle = testClient.startMessageMoveTask(DlqArn, maxNumberOfMessagesPerSecond = Some(1)).right.get

    // and: cancel the task after 2 seconds
    Thread.sleep(2000)
    val numMessagesMoved = testClient.cancelMessageMoveTask(taskHandle).right.get

    // and: fetch ApproximateNumberOfMessages
    val numMessagesInMainQueue = fetchApproximateNumberOfMessages(queue)
    val numMessagesInDlQueue = fetchApproximateNumberOfMessages(dlq)

    // then
    numMessagesMoved shouldEqual numMessagesInMainQueue

    // then: ApproximateNumberOfMessages should not change after 2 seconds
    Thread.sleep(2000)
    fetchApproximateNumberOfMessages(queue) shouldEqual numMessagesInMainQueue
    fetchApproximateNumberOfMessages(dlq) shouldEqual numMessagesInDlQueue
  }

  test("should fail to cancel non-existing task") {
    val randomTaskHandle = UUID.randomUUID().toString
    testClient.cancelMessageMoveTask(randomTaskHandle) shouldBe Left(
      SqsClientError(ResourceNotFound, s"The task handle ${'"'}$randomTaskHandle${'"'} is not valid or does not exist")
    )
  }

  private def createQueuesAndPopulateDlq(): (QueueUrl, QueueUrl) = {
    val dlq = testClient.createQueue("testQueue-dlq").toOption.get
    val redrivePolicy = RedrivePolicy("testQueue-dlq", awsRegion, awsAccountId, 1).toJson.compactPrint
    val queue =
      testClient.createQueue(
        "testQueue",
        attributes = Map(
          RedrivePolicyAttributeName -> redrivePolicy,
          VisibilityTimeoutAttributeName -> "1"
        )
      ).toOption.get

    // when: send messages
    for (i <- 0 until NumMessages) {
      testClient.sendMessage(queue, "Test message " + i)
    }

    // and: receive messages twice to make them move to DLQ
    receiveAllMessagesTwice(queue)

    // then: ensure that messages are in DLQ
    eventually(timeout(2.seconds), interval(100.millis)) {
      fetchApproximateNumberOfMessages(queue) shouldEqual 0
      fetchApproximateNumberOfMessages(dlq) shouldEqual NumMessages
    }

    (queue, dlq)
  }

  private def receiveAllMessagesTwice(queue: QueueUrl): Unit = {
    for (_ <- 0 until NumMessages) {
      testClient.receiveMessage(queue)
    }

    Thread.sleep(1500)
    for (_ <- 0 until NumMessages) {
      testClient.receiveMessage(queue)
    }
  }

  private def fetchApproximateNumberOfMessages(queueUrl: String): Int = {
    testClient
      .getQueueAttributes(queueUrl, ApproximateNumberOfMessagesAttributeName)(
        ApproximateNumberOfMessagesAttributeName.value
      )
      .toInt
  }
}

class MessageMoveTaskSdkV1Test extends MessageMoveTaskTest with SqsClientServerCommunication
class MessageMoveTaskSdkV2Test extends MessageMoveTaskTest with SqsClientServerWithSdkV2Communication
