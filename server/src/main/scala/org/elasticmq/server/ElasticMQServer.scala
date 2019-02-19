package org.elasticmq.server

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.actor.reply._
import org.elasticmq.rest.sqs.{CreateQueueDirectives, SQSRestServer, TheSQSRestServerBuilder}
import org.elasticmq.server.config.{CreateQueue, ElasticMQServerConfig}
import org.elasticmq.util.{Logging, NowProvider}
import org.elasticmq.{DeadLettersQueueData, MillisVisibilityTimeout, QueueData}
import org.joda.time.{DateTime, Duration}

import scala.concurrent.Await
import scala.concurrent.duration.Duration.Inf

class ElasticMQServer(config: ElasticMQServerConfig) extends Logging {
  val actorSystem = ActorSystem("elasticmq")

  def start() = {
    val queueManagerActor = createBase()
    val restServerOpt = optionallyStartRestSqs(queueManagerActor)

    createQueues(queueManagerActor)

    () =>
      {
        restServerOpt.map(_.stopAndGetFuture())
        Await.result(actorSystem.terminate(), Inf)
      }
  }

  private def createBase(): ActorRef = {
    config.storage match {
      case config.InMemoryStorage =>
        actorSystem.actorOf(Props(new QueueManagerActor(new NowProvider())))
    }
  }

  private def optionallyStartRestSqs(queueManagerActor: ActorRef): Option[SQSRestServer] = {
    if (config.restSqs.enabled) {

      val server = TheSQSRestServerBuilder(
        Some(actorSystem),
        Some(queueManagerActor),
        config.restSqs.bindHostname,
        config.restSqs.bindPort,
        config.nodeAddress,
        config.generateNodeAddress,
        config.restSqs.sqsLimits
      ).start()

      server.waitUntilStarted()

      Some(server)
    } else {
      None
    }

  }

  private def createQueues(queueManagerActor: ActorRef): Unit = {
    implicit val timeout = {
      import scala.concurrent.duration._
      Timeout(5.seconds)
    }

    config.createQueues.foreach { cq =>
      // Synchronously create queues since order matters
      val f = queueManagerActor ? org.elasticmq.msg.CreateQueue(configToParams(cq, new DateTime))
      Await.result(f, timeout.duration)
    }
  }

  private def configToParams(cq: CreateQueue, now: DateTime): QueueData = {
    QueueData(
      name = cq.name,
      defaultVisibilityTimeout = MillisVisibilityTimeout.fromSeconds(
        cq.defaultVisibilityTimeoutSeconds.getOrElse(CreateQueueDirectives.DefaultVisibilityTimeout)),
      delay = Duration.standardSeconds(cq.delaySeconds.getOrElse(CreateQueueDirectives.DefaultDelay)),
      receiveMessageWait = Duration.standardSeconds(
        cq.receiveMessageWaitSeconds.getOrElse(CreateQueueDirectives.DefaultReceiveMessageWait)),
      created = now,
      lastModified = now,
      deadLettersQueue = cq.deadLettersQueue.map(dlq => DeadLettersQueueData(dlq.name, dlq.maxReceiveCount)),
      isFifo = cq.isFifo,
      hasContentBasedDeduplication = cq.hasContentBasedDeduplication,
      copyMessagesTo = cq.copyMessagesTo,
      moveMessagesTo = cq.moveMessagesTo,
      tags = cq.tags,
      inflightMessagesLimit = cq.inflightMessagesLimit
    )
  }
}
