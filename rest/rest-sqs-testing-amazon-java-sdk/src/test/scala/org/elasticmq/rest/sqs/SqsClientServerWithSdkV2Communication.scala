package org.elasticmq.rest.sqs

import org.elasticmq.util.Logging
import org.elasticmq.{NodeAddress, RelaxedSQSLimits}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.{Args, BeforeAndAfter, Status}
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.{SqsClient, SqsClientBuilder}

import java.net.URI
import scala.util.Try

trait SqsClientServerWithSdkV2Communication extends AnyFunSuite with BeforeAndAfter with Logging {

  var clientV2: SqsClient = _ // strict server
  var relaxedClientV2: SqsClient = _

  var currentTestName: String = _

  var strictServer: SQSRestServer = _
  var relaxedServer: SQSRestServer = _
  val awsAccountId = "123456789012"
  val awsRegion = "elasticmq"

  val ServiceEndpoint = "http://localhost:9321"

  before {
    logger.info(s"\n---\nRunning test: $currentTestName\n---\n")

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

    clientV2 = SqsClient.builder()
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x")))
      .region(Region.EU_CENTRAL_1)
      .endpointOverride(new URI("http://localhost:9321"))
      .build()

    relaxedClientV2 = SqsClient.builder()
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

    logger.info(s"\n---\nTest done: $currentTestName\n---\n")
  }
}
