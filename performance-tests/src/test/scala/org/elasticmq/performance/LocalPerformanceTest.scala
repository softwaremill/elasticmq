package org.elasticmq.performance

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.sqs.model.{
  CreateQueueRequest,
  DeleteMessageRequest,
  ReceiveMessageRequest,
  SendMessageRequest
}
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClientBuilder}
import org.elasticmq._
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.actor.reply._
import org.elasticmq.msg._
import org.elasticmq.rest.sqs.{SQSRestServer, SQSRestServerBuilder}
import org.elasticmq.test._
import org.elasticmq.util.NowProvider
import org.joda.time.DateTime

import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._

object LocalPerformanceTest extends App {
  testAll()

  def testAll(): Unit = {
    val iterations = 10
    val msgsInIteration = 100000

    testWithMq(new ActorBasedMQ, 3, msgsInIteration, "in-memory warmup", 1)

    // testWithMq(new ActorBasedMQ, iterations, msgsInIteration, "in-memory", 1)
    // testWithMq(new ActorBasedMQ, iterations, msgsInIteration, "in-memory", 2)
    // testWithMq(new ActorBasedMQ, iterations, msgsInIteration, "in-memory", 3)
    testWithMq(new RestSQSMQ, iterations, msgsInIteration, "rest-sqs + in-memory", 1)
  }

  def testWithMq(mq: MQ, iterations: Int, msgsInIteration: Int, name: String, threadCount: Int): Unit = {
    println(
      "Running test for [%s], iterations: %d, msgs in iteration: %d, thread count: %d."
        .format(name, iterations, msgsInIteration, threadCount)
    )

    mq.start(StrictSQSLimits)

    val took = timed {
      val threads = for (i <- 1 to threadCount) yield {
        val t = new Thread(() => {
          runTest(mq, iterations, msgsInIteration, name + " " + i)
        })
        t.start()
        t
      }

      threads.foreach(_.join())
    }

    val count = msgsInIteration * iterations * threadCount
    println("Overall %s throughput: %f".format(name, count.toDouble / (took.toDouble / 1000.0)))
    println()

    mq.stop()
  }

  private def runTest(mq: MQ, iterations: Int, msgsInIteration: Int, name: String): Unit = {
    var count = 0

    val start = System.currentTimeMillis()
    for (i <- 1 to iterations) {
      val loopStart = System.currentTimeMillis()

      for (j <- 1 to msgsInIteration) {
        mq.sendMessage("Message" + (i * j))
      }

      for (_ <- 1 to msgsInIteration) {
        mq.receiveMessage()
      }

      count += msgsInIteration

      val loopEnd = System.currentTimeMillis()
      println(
        "%-20s throughput: %f, %d"
          .format(name, count.toDouble / ((loopEnd - start).toDouble / 1000.0), loopEnd - loopStart)
      )
    }
  }

  class ActorBasedMQ extends MQWithQueueManagerActor

  trait MQWithQueueManagerActor extends MQ {
    private var currentQueue: ActorRef = _

    implicit val timeout = Timeout(10000L, TimeUnit.MILLISECONDS)

    override def start(sqsLimits: Limits) = {
      val queueManagerActor = super.start(sqsLimits)

      currentQueue = Await
        .result(
          queueManagerActor ? CreateQueue(
            CreateQueueData.from(
              QueueData(
                "testQueue",
                MillisVisibilityTimeout(1000),
                org.joda.time.Duration.ZERO,
                org.joda.time.Duration.ZERO,
                new DateTime(),
                new DateTime()
              )
            )
          ),
          10.seconds
        )
        .right
        .get

      queueManagerActor
    }

    def sendMessage(m: String): Unit = {
      Await.result(
        currentQueue ? SendMessage(
          NewMessageData(None, m, Map.empty, ImmediateNextDelivery, None, None, 0, None, None)
        ),
        10.seconds
      )
    }

    def receiveMessage() = {
      val messages = Await.result(currentQueue ? ReceiveMessages(DefaultVisibilityTimeout, 1, None, None), 10.seconds)
      val message = messages.head
      Await.result(currentQueue ? DeleteMessage(message.deliveryReceipt.get), 10.seconds)
      message.content
    }
  }

  trait MQ {
    var actorSystem: ActorSystem = _

    def start(sqsLimits: Limits): ActorRef = {
      actorSystem = ActorSystem("performance-tests")
      val queueManagerActor = actorSystem.actorOf(Props(new QueueManagerActor(new NowProvider(), sqsLimits, None)))

      queueManagerActor
    }

    def stop(): Unit = {
      actorSystem.terminate()
    }

    def sendMessage(m: String): Unit

    def receiveMessage(): String
  }

  class RestSQSMQ extends MQ {
    private var currentSQSClient: AmazonSQS = _
    private var currentQueueUrl: String = _
    private var currentRestServer: SQSRestServer = _

    override def start(sqsLimits: Limits) = {
      val queueManagerActor = super.start(sqsLimits)

      currentRestServer = SQSRestServerBuilder
        .withActorSystem(actorSystem)
        .withQueueManagerActor(queueManagerActor)
        .start()

      currentSQSClient = AmazonSQSClientBuilder
        .standard()
        .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x")))
        .withEndpointConfiguration(new EndpointConfiguration("http://localhost:9324", "us-east-1"))
        .build()
      currentQueueUrl = currentSQSClient
        .createQueue(new CreateQueueRequest("testQueue").withAttributes(Map("VisibilityTimeout" -> "1").asJava))
        .getQueueUrl

      queueManagerActor
    }

    override def stop(): Unit = {
      currentRestServer.stopAndGetFuture
      currentSQSClient.shutdown()
      super.stop()
    }

    def sendMessage(m: String): Unit = {
      currentSQSClient.sendMessage(new SendMessageRequest(currentQueueUrl, m))
    }

    def receiveMessage() = {
      val msgs = currentSQSClient.receiveMessage(new ReceiveMessageRequest(currentQueueUrl)).getMessages
      if (msgs.size != 1) {
        throw new Exception(msgs.toString)
      }

      currentSQSClient.deleteMessage(new DeleteMessageRequest(currentQueueUrl, msgs.get(0).getReceiptHandle))

      msgs.get(0).getBody
    }
  }
}
