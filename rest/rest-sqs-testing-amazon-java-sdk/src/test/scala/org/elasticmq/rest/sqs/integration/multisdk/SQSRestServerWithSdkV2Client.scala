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
  val awsAccountId = "123456789012"
  val awsRegion = "elasticmq"

  val ServiceEndpoint = "http://localhost:9321"

  before {
    strictServer = SQSRestServerBuilder
      .withPort(9321)
      .withServerAddress(NodeAddress(port = 9321))
      .withAWSAccountId(awsAccountId)
      .withAWSRegion(awsRegion)
      .start()

    relaxedServer = SQSRestServerBuilder
      .withPort(9322)
      .withServerAddress(NodeAddress(port = 9322))
      .withSQSLimits(RelaxedSQSLimits)
      .start()

    strictServer.waitUntilStarted()
    relaxedServer.waitUntilStarted()

    clientV2 = AwsSqsClient
      .builder()
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x")))
      .region(Region.EU_CENTRAL_1)
      .endpointOverride(new URI("http://localhost:9321"))
      .build()

    testClient = new AwsSdkV2SqsClient(clientV2)

    relaxedClientV2 = AwsSqsClient
      .builder()
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x")))
      .region(Region.EU_CENTRAL_1)
      .endpointOverride(new URI("http://localhost:9322"))
      .build()
  }

  after {
    clientV2.close()
    relaxedClientV2.close()

    Try(strictServer.stopAndWait())
    Try(relaxedServer.stopAndWait())
  }
}
