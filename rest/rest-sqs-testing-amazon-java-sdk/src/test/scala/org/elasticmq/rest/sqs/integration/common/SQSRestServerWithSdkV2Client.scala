package org.elasticmq.rest.sqs.integration.common

import org.elasticmq.rest.sqs.integration.client.{AwsSdkV2SqsClient, SqsClient}
import org.elasticmq.rest.sqs.{SQSRestServer, SQSRestServerBuilder}
import org.elasticmq.util.Logging
import org.elasticmq.{NodeAddress, RelaxedSQSLimits}
import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.{SqsClient => AwsSqsClient}

import java.net.URI
import scala.util.Try

trait SQSRestServerWithSdkV2Client extends AnyFunSuite with BeforeAndAfter with Logging {

  var testClient: SqsClient = _

  var clientV2: AwsSqsClient = _ // strict server
  var relaxedClientV2: AwsSqsClient = _

  var currentTestName: String = _

  var strictServer: SQSRestServer = _
  var relaxedServer: SQSRestServer = _

  def awsAccountId: String = "123456789012"
  def awsRegion: String = "elasticmq"

  def serverPort: Int = 9321
  def serverContextPath: String = ""

  def relaxedServerPort: Int = 9322
  def relaxedServerContextPath: String = ""

  def serviceEndpoint = s"http://localhost:$serverPort${if (serverContextPath.nonEmpty) "/" + serverContextPath else ""}"

  def shouldStartServerAutomatically: Boolean = true

  def createServers(): Unit = {
    strictServer = SQSRestServerBuilder
      .withPort(serverPort)
      .withServerAddress(NodeAddress(port = serverPort, contextPath = serverContextPath))
      .withAWSAccountId(awsAccountId)
      .withAWSRegion(awsRegion)
      .start()

    relaxedServer = SQSRestServerBuilder
      .withPort(relaxedServerPort)
      .withServerAddress(NodeAddress(port = relaxedServerPort, contextPath = relaxedServerContextPath))
      .withSQSLimits(RelaxedSQSLimits)
      .start()

    strictServer.waitUntilStarted()
    relaxedServer.waitUntilStarted()
  }

  def createClients(): Unit = {
    clientV2 = AwsSqsClient
      .builder()
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x")))
      .region(Region.EU_CENTRAL_1)
      .endpointOverride(new URI(serviceEndpoint))
      .build()

    testClient = new AwsSdkV2SqsClient(clientV2)

    relaxedClientV2 = AwsSqsClient
      .builder()
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x")))
      .region(Region.EU_CENTRAL_1)
      .endpointOverride(new URI(s"http://localhost:$relaxedServerPort${if (relaxedServerContextPath.nonEmpty) "/" + relaxedServerContextPath else ""}"))
      .build()
  }

  def stopServers(): Unit = {
    if (strictServer != null) Try(strictServer.stopAndWait())
    if (relaxedServer != null) Try(relaxedServer.stopAndWait())
  }

  def stopClients(): Unit = {
    if (clientV2 != null) clientV2.close()
    if (relaxedClientV2 != null) relaxedClientV2.close()
  }

  before {
    if (shouldStartServerAutomatically) {
      createServers()
      createClients()
    }
  }

  after {
    if (shouldStartServerAutomatically) {
      stopClients()
      stopServers()
    }
  }
}
