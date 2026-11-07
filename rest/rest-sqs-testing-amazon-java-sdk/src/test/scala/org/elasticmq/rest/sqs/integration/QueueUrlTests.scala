package org.elasticmq.rest.sqs.integration

import org.elasticmq.NodeAddress
import org.elasticmq.rest.sqs.{SQSRestServer, SQSRestServerBuilder, TheSQSRestServerBuilder}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.{
  CreateQueueRequest,
  GetQueueUrlRequest,
  ListQueuesRequest,
  QueueAttributeName
}

import java.net.{URI, URLEncoder}
import java.nio.charset.StandardCharsets
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import scala.jdk.CollectionConverters._
import scala.util.Try

class QueueUrlTests extends AnyFunSuite with Matchers {

  private val accountId = "123456789012"

  private def withServer(builder: TheSQSRestServerBuilder)(test: Int => Unit): Unit = {
    val server: SQSRestServer = builder.withDynamicPort().withAWSAccountId(accountId).start()
    try test(server.waitUntilStarted().localAddress.getPort)
    finally Try(server.stopAndWait())
  }

  private def withClient(endpoint: String)(test: SqsClient => Unit): Unit = {
    val client = SqsClient
      .builder()
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x")))
      .region(Region.EU_CENTRAL_1)
      .endpointOverride(new URI(endpoint))
      .build()
    try test(client)
    finally Try(client.close())
  }

  private def createQueue(client: SqsClient, name: String): String =
    client.createQueue(CreateQueueRequest.builder().queueName(name).build()).queueUrl()

  test("by default, queue URLs use the host and port the client connected to") {
    withServer(SQSRestServerBuilder) { port =>
      withClient(s"http://localhost:$port") { client =>
        createQueue(client, "q1") shouldBe s"http://localhost:$port/$accountId/q1"
        client.getQueueUrl(GetQueueUrlRequest.builder().queueName("q1").build()).queueUrl() shouldBe
          s"http://localhost:$port/$accountId/q1"
        client.listQueues(ListQueuesRequest.builder().build()).queueUrls().asScala shouldBe
          List(s"http://localhost:$port/$accountId/q1")
      }
      withClient(s"http://127.0.0.1:$port") { client =>
        createQueue(client, "q1") shouldBe s"http://127.0.0.1:$port/$accountId/q1"
      }
    }
  }

  test("a request sent to a queue URL returns queue URLs based on the host, not on the request path") {
    withServer(SQSRestServerBuilder) { port =>
      val dlqUrl = s"http://localhost:$port/$accountId/dlq"
      withClient(s"http://localhost:$port") { client =>
        createQueue(client, "dlq")
        client.createQueue(
          CreateQueueRequest
            .builder()
            .queueName("source")
            .attributes(
              Map(
                QueueAttributeName.REDRIVE_POLICY ->
                  s"""{"deadLetterTargetArn":"arn:aws:sqs:elasticmq:$accountId:dlq","maxReceiveCount":"3"}"""
              ).asJava
            )
            .build()
        )
      }

      // SDK v1 (query protocol) sends ListDeadLetterSourceQueues to the queue URL
      val response = HttpClient
        .newHttpClient()
        .send(
          HttpRequest
            .newBuilder(new URI(dlqUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(
              HttpRequest.BodyPublishers.ofString(
                s"Action=ListDeadLetterSourceQueues&QueueUrl=${URLEncoder.encode(dlqUrl, StandardCharsets.UTF_8)}"
              )
            )
            .build(),
          HttpResponse.BodyHandlers.ofString()
        )

      withClue(response.body())(response.statusCode() shouldBe 200)
      response.body() should include(s"<QueueUrl>http://localhost:$port/$accountId/source</QueueUrl>")
    }
  }

  test("with a wildcard host, queue URLs include the configured context path") {
    withServer(SQSRestServerBuilder.withServerAddress(NodeAddress(host = "*", contextPath = "xyz"))) { port =>
      withClient(s"http://localhost:$port/xyz") { client =>
        createQueue(client, "q1") shouldBe s"http://localhost:$port/xyz/$accountId/q1"
      }
    }
  }

  test("a fixed server address is used regardless of the request") {
    withServer(SQSRestServerBuilder.withServerAddress(NodeAddress(host = "sqs.example.com", port = 1234))) { port =>
      withClient(s"http://localhost:$port") { client =>
        createQueue(client, "q1") shouldBe s"http://sqs.example.com:1234/$accountId/q1"
      }
    }
  }

  test("withServerAddressFromBinding uses the bound port") {
    withServer(SQSRestServerBuilder.withServerAddressFromBinding()) { port =>
      withClient(s"http://127.0.0.1:$port") { client =>
        createQueue(client, "q1") shouldBe s"http://localhost:$port/$accountId/q1"
      }
    }
  }
}
