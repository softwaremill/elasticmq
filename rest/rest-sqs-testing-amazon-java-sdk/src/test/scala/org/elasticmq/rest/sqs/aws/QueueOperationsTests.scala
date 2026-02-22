package org.elasticmq.rest.sqs.aws

import org.elasticmq.rest.sqs.client._
import scala.concurrent.duration.DurationInt

trait QueueOperationsTests extends AmazonJavaSdkNewTestBase {

  test("should create a queue") {
    testClient.createQueue("testQueue1").toOption.get
  }

  test("should get queue url") {
    // given
    testClient.createQueue("testQueue1").toOption.get

    // when
    val queueUrl = testClient.getQueueUrl("testQueue1").toOption.get

    // then
    queueUrl shouldEqual "http://localhost:9321/123456789012/testQueue1"
  }

  test("should list queues") {
    // given
    testClient.createQueue("testQueue1").toOption.get
    testClient.createQueue("testQueue2").toOption.get

    // when
    val queueUrls = testClient.listQueues()

    // then
    queueUrls shouldBe List(
      "http://localhost:9321/123456789012/testQueue1",
      "http://localhost:9321/123456789012/testQueue2"
    )
  }

  test("should list queues with specified prefix") {
    // given
    testClient.createQueue("aaa-testQueue1").toOption.get
    testClient.createQueue("bbb-testQueue2").toOption.get
    testClient.createQueue("bbb-testQueue3").toOption.get

    // when
    val queueUrls = testClient.listQueues(Some("bbb"))

    // then
    queueUrls shouldBe List(
      "http://localhost:9321/123456789012/bbb-testQueue2",
      "http://localhost:9321/123456789012/bbb-testQueue3"
    )
  }

  test("should fail to get queue url if queue doesn't exist") {
    testClient.getQueueUrl("testQueue1") shouldBe Left(
      SqsClientError(QueueDoesNotExist, "The specified queue does not exist.")
    )
  }

  test("should tag, untag and list queue tags") {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get

    // when
    testClient.tagQueue(queueUrl, Map("tag1" -> "value1", "tag2" -> "value2"))

    // then
    testClient.listQueueTags(queueUrl) shouldBe Map("tag1" -> "value1", "tag2" -> "value2")

    // when
    testClient.untagQueue(queueUrl, List("tag1"))

    // then
    testClient.listQueueTags(queueUrl) shouldBe Map("tag2" -> "value2")
  }

  test("should add permission") {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get

    // expect
    testClient.addPermission(queueUrl, "l", List(awsAccountId), List("get"))
  }

  test("should remove permission") {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get
    testClient.addPermission(queueUrl, "l", List(awsAccountId), List("get"))

    // expect
    testClient.removePermission(queueUrl, "l")
  }

  test("should delete queue") {
    // given
    val queueUrl = testClient.createQueue("testQueue1").toOption.get

    // when
    testClient.deleteQueue(queueUrl)

    // then
    eventually(timeout(5.seconds), interval(100.millis)) {
      testClient.listQueues() shouldBe empty
    }
  }
}
