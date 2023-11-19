package org.elasticmq.rest.sqs

import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.sqs.model._
import software.amazon.awssdk.services.sqs.model.{GetQueueUrlRequest => AwsSdkGetQueueUrlRequest}

class AmazonJavaSdkV2TestSuite extends SqsClientServerWithSdkV2Communication with Matchers {

  test("should create a queue") {
    clientV2.createQueue(CreateQueueRequest.builder().queueName("testQueue1").build())
  }

  test("should get queue url") {
    // Given
    clientV2.createQueue(CreateQueueRequest.builder().queueName("testQueue1").build())

    // When
    val queueUrl = clientV2.getQueueUrl(AwsSdkGetQueueUrlRequest.builder().queueName("testQueue1").build()).queueUrl()

    // Then
    queueUrl shouldEqual "http://localhost:9321/123456789012/testQueue1"
  }

  test("should fail to get queue url if queue doesn't exist") {
    // When
    val thrown = intercept[QueueDoesNotExistException] {
      clientV2.getQueueUrl(AwsSdkGetQueueUrlRequest.builder().queueName("testQueue1").build()).queueUrl()
    }

    // Then
    thrown.awsErrorDetails().errorCode() shouldBe "QueueDoesNotExist"
    thrown.awsErrorDetails().errorMessage() shouldBe "The specified queue does not exist."
  }

  test("should send and receive message") {
    val queue = clientV2.createQueue(CreateQueueRequest.builder().queueName("testQueue1").build())

    clientV2.sendMessage(SendMessageRequest.builder().queueUrl(queue.queueUrl()).messageBody("test msg 123").build())

    val messages = clientV2.receiveMessage(ReceiveMessageRequest.builder().queueUrl(queue.queueUrl()).build())

    System.err.println(messages)

    messages.messages().size() shouldBe 1
    messages.messages().get(0).body() shouldBe "test msg 123"
  }
}
