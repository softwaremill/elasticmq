package org.elasticmq.rest.sqs

import com.amazonaws.auth.{AWSCredentials, AWSCredentialsProvider}
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.sqs.AmazonSQSAsyncClient
import com.amazonaws.services.sqs.model.CreateQueueRequest
import org.scalatest.matchers.should.Matchers
import org.springframework.cloud.aws.messaging.core.QueueMessagingTemplate

class SpringAWSTestSuite extends SqsClientServerCommunication with Matchers {

  def withSpringClient(body: QueueMessagingTemplate => Unit) = {
    val sqs = AmazonSQSAsyncClient
      .asyncBuilder()
      .withCredentials(new AWSCredentialsProvider {
        override def getCredentials: AWSCredentials = null
        override def refresh(): Unit = ()
      })
      .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(ServiceEndpoint, "us-west-1"))
      .build()

    val template = new QueueMessagingTemplate(sqs)
    try {
      body(template)
    } finally (sqs.shutdown())
  }

  test("should send message with spring aws") {

    withSpringClient { template =>
      // given
      val queue = client.createQueue(new CreateQueueRequest("spring-test-queue"))

      // when ~> then
      template.convertAndSend(queue.getQueueUrl, "hello")

    }
  }

}
