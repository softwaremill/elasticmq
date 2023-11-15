package org.elasticmq.rest.sqs

import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.sqs.model._

class AmazonJavaSdkV2TestSuite extends SqsClientServerWithSdkV2Communication with Matchers {

  test("should create a queue") {
    clientV2.createQueue(CreateQueueRequest.builder().queueName("testQueue1").build())
  }

  test("should get queue url") {
    // Given
    clientV2.createQueue(CreateQueueRequest.builder().queueName("testQueue1").build())

    // When
    val queueUrl = clientV2.getQueueUrl(GetQueueUrlRequest.builder().queueName("testQueue1").build()).queueUrl()

    // Then
    queueUrl shouldEqual "http://localhost:9321/123456789012/testQueue1"
  }

  test("should fail to get queue url if queue doesn't exist") {
    // When
    val thrown = intercept[QueueDoesNotExistException] {
      clientV2.getQueueUrl(GetQueueUrlRequest.builder().queueName("testQueue1").build()).queueUrl()
    }

    // Then
    thrown.awsErrorDetails().errorCode() shouldBe "QueueDoesNotExist"
    thrown.awsErrorDetails().errorMessage() shouldBe "The specified queue does not exist."
  }
}
