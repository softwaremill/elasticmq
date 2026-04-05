package org.elasticmq.rest.sqs.integration

import org.elasticmq.persistence.CreateQueueMetadata
import org.elasticmq.rest.sqs.{AutoCreateQueuesConfig, SQSRestServerBuilder}
import org.elasticmq.{NodeAddress, RelaxedSQSLimits}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.{SqsClient => AwsSqsClient}

import java.net.URI
import scala.util.Try

class AutoCreateQueuesTests extends AnyFunSuite with Matchers {

  private val autoCreatePort = 9334
  private val awsAccountId = "123456789012"
  private val awsRegion = "elasticmq"

  private def withAutoCreateServer(
      template: CreateQueueMetadata = CreateQueueMetadata(name = ""),
      port: Int = autoCreatePort
  )(test: AwsSqsClient => Unit): Unit = {
    val server = SQSRestServerBuilder
      .withPort(port)
      .withServerAddress(NodeAddress(port = port))
      .withSQSLimits(RelaxedSQSLimits)
      .withAWSAccountId(awsAccountId)
      .withAWSRegion(awsRegion)
      .withAutoCreateQueues(AutoCreateQueuesConfig(enabled = true, template = template))
      .start()
    server.waitUntilStarted()

    val client = AwsSqsClient
      .builder()
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x")))
      .region(Region.EU_CENTRAL_1)
      .endpointOverride(new URI(s"http://localhost:$port"))
      .build()

    try {
      test(client)
    } finally {
      Try(client.close())
      Try(server.stopAndWait())
    }
  }

  test("should auto-create a queue when sending a message to a non-existing queue") {
    withAutoCreateServer() { client =>
      val queueUrl = s"http://localhost:$autoCreatePort/$awsAccountId/new-queue"

      val sendResult = client.sendMessage(
        software.amazon.awssdk.services.sqs.model.SendMessageRequest.builder()
          .queueUrl(queueUrl)
          .messageBody("hello")
          .build()
      )
      sendResult.messageId() should not be empty

      val listResult = client.listQueues(
        software.amazon.awssdk.services.sqs.model.ListQueuesRequest.builder().build()
      )
      listResult.queueUrls() should contain(queueUrl)
    }
  }

  test("should auto-create a FIFO queue when queue name ends with .fifo") {
    withAutoCreateServer() { client =>
      val queueUrl = s"http://localhost:$autoCreatePort/$awsAccountId/new-fifo.fifo"

      val sendResult = client.sendMessage(
        software.amazon.awssdk.services.sqs.model.SendMessageRequest.builder()
          .queueUrl(queueUrl)
          .messageBody("hello fifo")
          .messageGroupId("group1")
          .messageDeduplicationId("dedup1")
          .build()
      )
      sendResult.messageId() should not be empty

      val attrResult = client.getQueueAttributes(
        software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest.builder()
          .queueUrl(queueUrl)
          .attributeNamesWithStrings("FifoQueue")
          .build()
      )
      attrResult.attributesAsStrings().get("FifoQueue") shouldEqual "true"
    }
  }

  test("should auto-create queue with template parameters applied") {
    val templateWithDelay = CreateQueueMetadata(name = "", delaySeconds = Some(5L))
    withAutoCreateServer(template = templateWithDelay, port = 9336) { client =>
      val queueUrl = s"http://localhost:9336/$awsAccountId/templated-queue"
      client.sendMessage(
        software.amazon.awssdk.services.sqs.model.SendMessageRequest.builder()
          .queueUrl(queueUrl)
          .messageBody("hello")
          .build()
      )

      val attrResult = client.getQueueAttributes(
        software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest.builder()
          .queueUrl(queueUrl)
          .attributeNamesWithStrings("DelaySeconds")
          .build()
      )
      attrResult.attributesAsStrings().get("DelaySeconds") shouldEqual "5"
    }
  }
}
