package org.elasticmq.server

import akka.util.Timeout
import org.elasticmq.{MillisVisibilityTimeout, QueueData}
import org.elasticmq.msg.CreateQueue
import org.elasticmq.util.Logging
import org.elasticmq.rest.sqs.{CreateQueueDirectives, TheSQSRestServerBuilder, SQSRestServer}
import akka.actor.{Props, ActorRef, ActorSystem}
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.util.NowProvider
import org.elasticmq.actor.reply._
import org.joda.time.{DateTime, Duration}
import scala.concurrent.Await

class ElasticMQServer(config: ElasticMQServerConfig) extends Logging {
  val actorSystem = ActorSystem("elasticmq")

  def start() = {
    val queueManagerActor = createBase()
    val restServerOpt = optionallyStartRestSqs(queueManagerActor)

    createQueues(queueManagerActor)

    () => {
      restServerOpt.map(_.stopAndGetFuture())
      actorSystem.shutdown()
      actorSystem.awaitTermination()
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

      val server = TheSQSRestServerBuilder(Some(actorSystem),
        Some(queueManagerActor),
        config.restSqs.bindHostname,
        config.restSqs.bindPort,
        config.nodeAddress,
        config.generateNodeAddress,
        config.restSqs.sqsLimits).start()

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

    val futures = config.createQueues.map { cq =>
      queueManagerActor ? CreateQueue(configToParams(cq, new DateTime))
    }

    futures.foreach { f => Await.result(f, timeout.duration) }
  }

  private def configToParams(cq: config.CreateQueue, now: DateTime): QueueData = {
    QueueData(
      name = cq.name,
      defaultVisibilityTimeout = MillisVisibilityTimeout.fromSeconds(
        cq.defaultVisibilityTimeoutSeconds.getOrElse(CreateQueueDirectives.DefaultVisibilityTimeout)),
      delay = Duration.standardSeconds(cq.delaySeconds.getOrElse(CreateQueueDirectives.DefaultDelay)),
      receiveMessageWait = Duration.standardSeconds(
        cq.receiveMessageWaitSeconds.getOrElse(CreateQueueDirectives.DefaultReceiveMessageWaitTimeSecondsAttribute)),
      maxReceiveCount = cq.maxReceiveCount,
      created = now,
      lastModified = now,
      deadLettersQueue = cq.deadLettersQueue.map(configToParams(_, now)))
  }
}
