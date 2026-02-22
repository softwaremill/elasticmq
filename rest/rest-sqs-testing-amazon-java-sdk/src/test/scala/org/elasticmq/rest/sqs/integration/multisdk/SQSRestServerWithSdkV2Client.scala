package org.elasticmq.rest.sqs.integration.multisdk

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

  before {
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

  after {
    clientV2.close()
    relaxedClientV2.close()

    Try(strictServer.stopAndWait())
    Try(relaxedServer.stopAndWait())
  }
}
