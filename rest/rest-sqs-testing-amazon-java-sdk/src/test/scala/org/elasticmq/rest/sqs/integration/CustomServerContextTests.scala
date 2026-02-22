package org.elasticmq.rest.sqs.integration

import org.elasticmq.NodeAddress
import org.elasticmq.rest.sqs.integration.client.{AwsSdkV2SqsClient, SqsClient}
import org.elasticmq.rest.sqs.integration.multisdk.AmazonJavaMultiSdkTestBase
import org.elasticmq.rest.sqs.{SQSRestServer, SQSRestServerBuilder}
import org.scalatest.BeforeAndAfter
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.{SqsClient => AwsSqsClient}

import java.net.URI
import scala.util.Try

class CustomServerContextTests extends AmazonJavaMultiSdkTestBase with BeforeAndAfter {

  var testClient: SqsClient = _
  var clientV2: AwsSqsClient = _
  var server: SQSRestServer = _

  val awsAccountId = "123456789012"
  val awsRegion = "elasticmq"
  val port = 9324
  val contextPath = "xyz"

  before {
    server = SQSRestServerBuilder
      .withPort(port)
      .withServerAddress(NodeAddress(port = port, contextPath = contextPath))
      .withAWSAccountId(awsAccountId)
      .withAWSRegion(awsRegion)
      .start()

    server.waitUntilStarted()

    clientV2 = AwsSqsClient
      .builder()
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x")))
      .region(Region.US_EAST_1)
      .endpointOverride(new URI(s"http://localhost:$port/$contextPath"))
      .build()

    testClient = new AwsSdkV2SqsClient(clientV2)
  }

  after {
    clientV2.close()
    Try(server.stopAndWait())
  }

  test("should create queue when context path is set") {
    val queueUrl = testClient.createQueue("testQueue").toOption.get
    queueUrl should be("http://localhost:9324/xyz/123456789012/testQueue")
  }

  test("should send and receive messages when context path is set") {
    val queueUrl = testClient.createQueue("testQueue").toOption.get

    testClient.sendMessage("http://localhost:9324/xyz/123456789012/testQueue", "test msg1").isRight shouldBe true
    testClient.sendMessage("http://localhost:9324/xyz/queue/testQueue", "test msg2").isRight shouldBe true
    testClient.sendMessage("http://localhost:9324/xyz/abc/testQueue", "test msg3").isRight shouldBe true
    testClient.sendMessage("http://localhost:9324/xyz/testQueue", "test msg4").isRight shouldBe true

    val messages = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(10))

    messages.map(_.body) should contain allOf ("test msg1", "test msg2", "test msg3", "test msg4")
  }
}
