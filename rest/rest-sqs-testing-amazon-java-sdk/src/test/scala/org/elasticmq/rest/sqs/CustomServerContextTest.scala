package org.elasticmq.rest.sqs

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClientBuilder}
import org.elasticmq.NodeAddress
import org.elasticmq.util.Logging
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfter, OptionValues}

import scala.collection.JavaConverters._
import scala.util.Try

class CustomServerContextTest extends AnyFunSuite with BeforeAndAfter with Logging with Matchers with OptionValues {

  var client: AmazonSQS = _
  var server: SQSRestServer = _

  before {
    server = SQSRestServerBuilder
      .withPort(9321)
      .withServerAddress(NodeAddress(port = 9321, contextPath = "xyz"))
      .withAWSAccountId("123456789012")
      .withAWSRegion("elasticmq")
      .start()

    client = AmazonSQSClientBuilder
      .standard()
      .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x")))
      .withEndpointConfiguration(new EndpointConfiguration("http://localhost:9321/xyz", "us-east-1"))
      .build()
  }

  after {
    client.shutdown()
    Try(server.stopAndWait())
  }

  test("should create queue when context path is set") {
    val queue = client.createQueue("testQueue")
    queue.getQueueUrl should be("http://localhost:9321/xyz/123456789012/testQueue")
  }

  test("should send and receive messages when context path is set") {
    val queue = client.createQueue("testQueue")

    client.sendMessage("http://localhost:9321/xyz/123456789012/testQueue", "test msg1")
    client.sendMessage("http://localhost:9321/xyz/queue/testQueue", "test msg2")
    client.sendMessage("http://localhost:9321/xyz/abc/testQueue", "test msg3")
    client.sendMessage("http://localhost:9321/xyz/testQueue", "test msg4")

    val message = client.receiveMessage(new ReceiveMessageRequest(queue.getQueueUrl).withMaxNumberOfMessages(10))

    message.getMessages.asScala.map(_.getBody) should contain allOf ("test msg1", "test msg2", "test msg3", "test msg4")
  }
}
