package org.elasticmq.rest.sqs.aws

import org.elasticmq.rest.sqs.client._
import org.elasticmq.rest.sqs.model.RedrivePolicy
import org.elasticmq.rest.sqs.model.RedrivePolicyJson.format
import spray.json.enrichAny

trait DeadLetterQueueTests extends AmazonJavaSdkNewTestBase {

  test("should list DeadLetterQueueSourceArn in receive message attributes") {
    // given
    val dlQueue = testClient.createQueue("testDlq").toOption.get
    val queue = testClient
      .createQueue(
        "testQueue1",
        Map(
          VisibilityTimeoutAttributeName -> "0",
          RedrivePolicyAttributeName -> RedrivePolicy("testDlq", awsRegion, awsAccountId, 1).toJson.compactPrint
        )
      )
      .toOption
      .get

    // when
    testClient.sendMessage(queue, "test123")
    val firstReceiveResult = testClient.receiveMessage(queue, List("All"))
    val secondReceiveResult = testClient.receiveMessage(queue, List("All"))
    val dlqReceiveResult = testClient.receiveMessage(dlQueue, List("All"))

    // then
    firstReceiveResult.flatMap(_.attributes.keys.toList) should not contain DeadLetterQueueSourceArn
    secondReceiveResult shouldBe empty
    dlqReceiveResult.flatMap(_.attributes.toList) should contain(
      (DeadLetterQueueSourceArn, s"arn:aws:sqs:$awsRegion:$awsAccountId:testQueue1")
    )
  }

  test("should list all source queues for a dlq") {
    // given
    val dlqUrl = testClient.createQueue("testDlq").toOption.get
    val redrivePolicy = RedrivePolicy("testDlq", awsRegion, awsAccountId, 1).toJson.toString

    val qUrls = for (i <- 1 to 3) yield {
      testClient
        .createQueue(
          "q" + i,
          Map(RedrivePolicyAttributeName -> redrivePolicy)
        )
        .toOption
        .get
    }

    // when
    val result = testClient.listDeadLetterSourceQueues(dlqUrl)

    // then
    result should contain theSameElementsAs (qUrls)
  }

  test("should list dead letter source queues") {
    // given
    val dlq1Url = testClient.createQueue("testDlq1").toOption.get
    val redrivePolicyJson = RedrivePolicy("testDlq1", awsRegion, awsAccountId, 3).toJson.compactPrint
    val queue1Url =
      testClient.createQueue("testQueue1", Map(RedrivePolicyAttributeName -> redrivePolicyJson)).toOption.get
    val queue2Url =
      testClient.createQueue("testQueue2", Map(RedrivePolicyAttributeName -> redrivePolicyJson)).toOption.get
    val queue4Url =
      testClient.createQueue("testQueue4", Map(RedrivePolicyAttributeName -> redrivePolicyJson)).toOption.get
    testClient.createQueue("testDlq2").toOption.get
    testClient
      .createQueue(
        "testQueue3",
        Map(RedrivePolicyAttributeName -> RedrivePolicy("testDlq2", awsRegion, awsAccountId, 3).toJson.compactPrint)
      )
      .toOption
      .get
    testClient.createQueue("testQueue5").toOption.get

    // when
    val result = testClient.listDeadLetterSourceQueues(dlq1Url)

    // then
    result should contain theSameElementsAs Set(queue1Url, queue2Url, queue4Url)
  }
}
