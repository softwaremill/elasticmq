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
      case config.InMemoryStorage => {
        actorSystem.actorOf(Props(new QueueManagerActor(new NowProvider())))
      }
    }
  }

  private def optionallyStartRestSqs(queueManagerActor: ActorRef): Option[SQSRestServer] = {
    if (config.restSqs.enabled) {

      val server = TheSQSRestServerBuilder(Some(actorSystem),
        Some(queueManagerActor),
        config.restSqs.bindHostname,
        config.restSqs.bindPort,
        config.nodeAddress,
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
      queueManagerActor ? CreateQueue(QueueData(cq.name,
        MillisVisibilityTimeout.fromSeconds(cq.defaultVisibilityTimeoutSeconds.getOrElse(CreateQueueDirectives.DefaultVisibilityTimeout)),
        Duration.standardSeconds(cq.delaySeconds.getOrElse(CreateQueueDirectives.DefaultDelay)),
        Duration.standardSeconds(cq.receiveMessageWaitSeconds.getOrElse(CreateQueueDirectives.DefaultReceiveMessageWaitTimeSecondsAttribute)),
        new DateTime(),
        new DateTime()))
    }

    futures.foreach { f => Await.result(f, timeout.duration) }
  }
}
