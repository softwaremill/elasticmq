package org.elasticmq.server

import com.typesafe.scalalogging.slf4j.Logging
import org.elasticmq.rest.sqs.{SQSRestServer, SQSRestServerBuilder}
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

      val server = SQSRestServerBuilder(Some(actorSystem),
        Some(queueManagerActor),
        config.restSqs.bindHostname,
        config.restSqs.bindPort,
        config.nodeAddress,
        config.restSqs.sqsLimits).start()

      Some(server)
    } else {
      None
    }
  }
}
