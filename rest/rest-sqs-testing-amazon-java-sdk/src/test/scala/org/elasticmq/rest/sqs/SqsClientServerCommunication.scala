package org.elasticmq.rest.sqs

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.sqs.model.{Message, ReceiveMessageRequest}
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClientBuilder}
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import org.elasticmq.util.Logging
import org.elasticmq.{MessagePersistenceConfig, NodeAddress, RelaxedSQSLimits}
import org.scalatest.{Args, BeforeAndAfter, Status}
import org.scalatest.funsuite.AnyFunSuite

import scala.collection.JavaConverters._
import scala.util.Try

trait SqsClientServerCommunication extends AnyFunSuite with BeforeAndAfter with Logging {

  var client: AmazonSQS = _ // strict server
  var relaxedClient: AmazonSQS = _
  var httpClient: CloseableHttpClient = _

  var currentTestName: String = _

  var strictServer: SQSRestServer = _
  var relaxedServer: SQSRestServer = _
  val awsAccountId = "123456789012"
  val awsRegion = "elasticmq"

  def messagePersistenceConfig: MessagePersistenceConfig

  before {
    logger.info(s"\n---\nRunning test: $currentTestName\n---\n")

    strictServer = SQSRestServerBuilder
      .withPort(9321)
      .withServerAddress(NodeAddress(port = 9321))
      .withAWSAccountId(awsAccountId)
      .withAWSRegion(awsRegion)
      .withMessagePersistenceConfig(messagePersistenceConfig)
      .start()

    relaxedServer = SQSRestServerBuilder
      .withPort(9322)
      .withServerAddress(NodeAddress(port = 9322))
      .withSQSLimits(RelaxedSQSLimits)
      .withMessagePersistenceConfig(messagePersistenceConfig)
      .start()

    strictServer.waitUntilStarted()
    relaxedServer.waitUntilStarted()

    client = AmazonSQSClientBuilder
      .standard()
      .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x")))
      .withEndpointConfiguration(new EndpointConfiguration("http://localhost:9321", "us-east-1"))
      .build()

    relaxedClient = AmazonSQSClientBuilder
      .standard()
      .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x")))
      .withEndpointConfiguration(new EndpointConfiguration("http://localhost:9322", "us-east-1"))
      .build()

    httpClient = HttpClients.createDefault()
  }

  after {
    client.shutdown()
    relaxedClient.shutdown()
    httpClient.close()

    // TODO: Figure out why this intermittently isn't able to unbind cleanly
    Try(strictServer.stopAndWait())
    Try(relaxedServer.stopAndWait())

    logger.info(s"\n---\nTest done: $currentTestName\n---\n")
  }

  override protected def runTest(testName: String, args: Args): Status = {
    currentTestName = testName
    val result = super.runTest(testName, args)
    currentTestName = null
    result
  }

  def receiveSingleMessageObject(queueUrl: String): Option[Message] = {
    receiveSingleMessageObject(queueUrl, List("All"))
  }

  def receiveSingleMessageObject(queueUrl: String, requestedAttributes: List[String]): Option[Message] = {
    client
      .receiveMessage(new ReceiveMessageRequest(queueUrl).withMessageAttributeNames(requestedAttributes.asJava))
      .getMessages
      .asScala
      .headOption
  }

  def receiveSingleMessage(queueUrl: String): Option[String] = {
    receiveSingleMessage(queueUrl, List("All"))
  }

  def receiveSingleMessage(queueUrl: String, requestedAttributes: List[String]): Option[String] = {
    val messages = client.receiveMessage(new ReceiveMessageRequest(queueUrl)).getMessages.asScala
    messages.headOption.map(_.getBody)
  }
}
