package org.elasticmq.rest.sqs

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model._
import org.apache.http.HttpHost
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.message.BasicNameValuePair
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.elasticmq._
import org.elasticmq.rest.sqs.model.RedrivePolicy
import org.elasticmq.rest.sqs.model.RedrivePolicyJson.format
import org.scalatest.matchers.should.Matchers
import spray.json.enrichAny

import java.net.URI
import java.nio.ByteBuffer
import java.util.UUID
import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.control.Exception._

class MessageMoveTaskTest extends SqsClientServerCommunication with Matchers {

  test("blah blah blah") {
    val dlq = client.createQueue(new CreateQueueRequest("testQueue-dlq"))
    val redrivePolicy = RedrivePolicy("testQueue-dlq", awsRegion, awsAccountId, 1).toJson.toString()
    val q =
      client.createQueue(
        new CreateQueueRequest("testQueue")
          .addAttributesEntry(redrivePolicyAttribute, redrivePolicy)
          .addAttributesEntry("VisibilityTimeout", "1")
      )

    val volume = 6
    for (i <- 0 until volume) {
      client.sendMessage(q.getQueueUrl, "Test message " + i)
    }

    for (i <- 0 until volume) {
      client.receiveMessage(q.getQueueUrl)
    }

    Thread.sleep(2000)
    for (i <- 0 until volume) {
      client.receiveMessage(q.getQueueUrl)
    }

    Thread.sleep(2000)
    println(
      client.getQueueAttributes(
        new GetQueueAttributesRequest().withQueueUrl(q.getQueueUrl).withAttributeNames("ApproximateNumberOfMessages")
      )
    )
    println(
      client.getQueueAttributes(
        new GetQueueAttributesRequest().withQueueUrl(dlq.getQueueUrl).withAttributeNames("ApproximateNumberOfMessages")
      )
    )

    val response = client.startMessageMoveTask(
      new StartMessageMoveTaskRequest().withSourceArn(s"arn:aws:sqs:$awsRegion:$awsAccountId:testQueue-dlq")
    )
    println(response.getTaskHandle)

    Thread.sleep(5000)
    println(
      client.getQueueAttributes(
        new GetQueueAttributesRequest().withQueueUrl(q.getQueueUrl).withAttributeNames("ApproximateNumberOfMessages")
      )
    )
    println(
      client.getQueueAttributes(
        new GetQueueAttributesRequest().withQueueUrl(dlq.getQueueUrl).withAttributeNames("ApproximateNumberOfMessages")
      )
    )
  }
}
