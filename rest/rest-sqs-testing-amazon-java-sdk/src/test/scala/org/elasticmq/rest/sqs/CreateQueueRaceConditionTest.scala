package org.elasticmq.rest.sqs
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.sqs.model.CreateQueueRequest
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClientBuilder}
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.Executors
import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.Try

class CreateQueueRaceConditionTest extends SqsClientServerCommunication with Matchers with OptionValues {

  test("should create one queue and return its address for every request with the same name and metadata") {
    val clients: Seq[AmazonSQS] = (0 until 100).map { _ =>
      AmazonSQSClientBuilder
        .standard()
        .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x")))
        .withEndpointConfiguration(new EndpointConfiguration("http://localhost:9321", "us-east-1"))
        .build()
    }

    implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(100))

    val tasks = clients.map(client => Future { Try(client.createQueue("abc")).toEither })
    val results = Await.result(Future.sequence(tasks), Duration.Inf)

    results.map(_.map(_.getQueueUrl)) should be(Vector.fill(100)(Right("http://localhost:9321/123456789012/abc")))
  }

  test("should create one queue and return an error for every request with the same name and different metadata") {
    val clients: Seq[AmazonSQS] = (0 until 100).map { _ =>
      AmazonSQSClientBuilder
        .standard()
        .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x")))
        .withEndpointConfiguration(new EndpointConfiguration("http://localhost:9321", "us-east-1"))
        .build()
    }

    implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(100))

    val tasks = clients.zipWithIndex.map { case (client, index) =>
      Future {
        Try(
          client.createQueue(
            new CreateQueueRequest("abc")
              .withAttributes(Map("VisibilityTimeout" -> (10 + index).toString).asJava)
          )
        ).toEither
      }
    }
    val results = Await.result(Future.sequence(tasks), Duration.Inf)

    val grouped = results.groupBy(_.isLeft)
    val rights = grouped(false)
    val lefts = grouped(true)
    rights.length should be(1)
    lefts.length should be(99)
  }
}
