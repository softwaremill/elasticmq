package org.elasticmq.performance

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.sqs.AmazonSQSClient
import com.amazonaws.services.sqs.model.{CreateQueueRequest, DeleteMessageRequest, ReceiveMessageRequest, SendMessageRequest}
import org.elasticmq.{NewMessageData, QueueData, _}
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.actor.reply._
import org.elasticmq.msg.{CreateQueue, ReceiveMessages, SendMessage, _}
import org.elasticmq.rest.sqs.{SQSRestServer, SQSRestServerBuilder}
import org.elasticmq.test._
import org.elasticmq.util.NowProvider
import org.joda.time.DateTime

import scala.collection.JavaConversions._
import scala.concurrent.Await
import scala.concurrent.duration._

object LocalPerformanceTest extends App {
  testAll()

  def testAll() {
    val iterations = 10
    val msgsInIteration = 100000

    testWithMq(new ActorBasedMQ, 3, 100000, "in-memory warmup", 1)

    //testWithMq(new ActorBasedMQ, iterations, msgsInIteration, "in-memory", 1)
    //testWithMq(new ActorBasedMQ, iterations, msgsInIteration, "in-memory", 2)
    //testWithMq(new ActorBasedMQ, iterations, msgsInIteration, "in-memory", 3)
    testWithMq(new RestSQSMQ, iterations, msgsInIteration, "rest-sqs + in-memory", 1)
  }

  def testWithMq(mq: MQ, iterations: Int, msgsInIteration: Int, name: String, threadCount: Int) {
    println(
      "Running test for [%s], iterations: %d, msgs in iteration: %d, thread count: %d."
        .format(name, iterations, msgsInIteration, threadCount))

    mq.start()

    val took = timed {
      val threads = for (i <- 1 to threadCount) yield {
        val t = new Thread(new Runnable() {
          def run() { runTest(mq, iterations, msgsInIteration, name + " " + i) }
        })
        t.start()
        t
      }

      threads.foreach(_.join())
    }

    val count = msgsInIteration * iterations * threadCount
    println("Overall %s throughput: %f".format(name, (count.toDouble / (took.toDouble / 1000.0))))
    println()

    mq.stop()
  }

  private def runTest(mq: MQ, iterations: Int, msgsInIteration: Int, name: String) {
    var count = 0

    val start = System.currentTimeMillis()
    for (i <- 1 to iterations) {
      val loopStart = System.currentTimeMillis()

      for (j <- 1 to msgsInIteration) {
        mq.sendMessage("Message" + (i * j))
      }

      for (j <- 1 to msgsInIteration) {
        mq.receiveMessage()
      }

      count += msgsInIteration

      val loopEnd = System.currentTimeMillis()
      println(
        "%-20s throughput: %f, %d"
          .format(name, (count.toDouble / ((loopEnd - start).toDouble / 1000.0)), (loopEnd - loopStart)))
    }
  }

  class ActorBasedMQ extends MQWithQueueManagerActor

  trait MQWithQueueManagerActor extends MQ {
    private var currentQueue: ActorRef = _

    implicit val timeout = Timeout(10000L, TimeUnit.MILLISECONDS)

    override def start() = {
      val queueManagerActor = super.start()

      currentQueue = Await
        .result(
          queueManagerActor ? CreateQueue(
            QueueData("testQueue",
              MillisVisibilityTimeout(1000),
              org.joda.time.Duration.ZERO,
              org.joda.time.Duration.ZERO,
              new DateTime(),
              new DateTime())),
          10.seconds
        )
        .right
        .get

      queueManagerActor
    }

    def sendMessage(m: String) {
      Await.result(currentQueue ? SendMessage(NewMessageData(None, m, Map.empty, ImmediateNextDelivery, None, None)),
        10.seconds)
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

    def start(): ActorRef = {
      actorSystem = ActorSystem("performance-tests")
      val queueManagerActor = actorSystem.actorOf(Props(new QueueManagerActor(new NowProvider())))

      queueManagerActor
    }

    def stop() {
      actorSystem.terminate()
    }

    def sendMessage(m: String)

    def receiveMessage(): String
  }

  class RestSQSMQ extends MQ {
    private var currentSQSClient: AmazonSQSClient = _
    private var currentQueueUrl: String = _
    private var currentRestServer: SQSRestServer = _

    override def start() = {
      val queueManagerActor = super.start()

      currentRestServer = SQSRestServerBuilder
        .withActorSystem(actorSystem)
        .withQueueManagerActor(queueManagerActor)
        .start()

      currentSQSClient = new AmazonSQSClient(new BasicAWSCredentials("x", "x"))
      currentSQSClient.setEndpoint("http://localhost:9324")
      currentQueueUrl = currentSQSClient
        .createQueue(new CreateQueueRequest("testQueue").withAttributes(Map("VisibilityTimeout" -> "1")))
        .getQueueUrl

      queueManagerActor
    }

    override def stop() {
      currentRestServer.stopAndGetFuture
      currentSQSClient.shutdown()
      super.stop()
    }

    def sendMessage(m: String) {
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
