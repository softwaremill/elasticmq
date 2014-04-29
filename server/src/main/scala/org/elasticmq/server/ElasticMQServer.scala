package org.elasticmq.server

import org.elasticmq.util.Logging
import org.elasticmq.rest.sqs.{TheSQSRestServerBuilder, SQSRestServer}
import akka.actor.{Props, ActorRef, ActorSystem}
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.util.NowProvider

class ElasticMQServer(config: ElasticMQServerConfig) extends Logging {
  val actorSystem = ActorSystem("elasticmq")

  def start() = {
    val queueManagerActor = createBase()
    val restServerOpt = optionallyStartRestSqs(queueManagerActor)

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
}
