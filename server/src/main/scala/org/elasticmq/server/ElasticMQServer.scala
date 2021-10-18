package org.elasticmq.server

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout
import org.elasticmq.ElasticMQError
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.actor.queue.Restore
import org.elasticmq.actor.reply._
import org.elasticmq.persistence.file.ConfigBasedQueuePersistenceActor
import org.elasticmq.persistence.sql.SqlQueuePersistenceActor
import org.elasticmq.rest.sqs.{SQSRestServer, TheSQSRestServerBuilder}
import org.elasticmq.rest.stats.{StatisticsRestServer, TheStatisticsRestServerBuilder}
import org.elasticmq.server.config.ElasticMQServerConfig
import org.elasticmq.util.{Logging, NowProvider}

import scala.concurrent.duration.Duration.Inf
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

class ElasticMQServer(config: ElasticMQServerConfig) extends Logging {
  val actorSystem = ActorSystem("elasticmq")

  def start() = {
    val queueConfigStore: Option[ActorRef] = createQueueEventListener
    val queueManagerActor = createBase(queueConfigStore)
    val restServerOpt = optionallyStartRestSqs(queueManagerActor, queueConfigStore)
    val restStatisticsServerOpt = optionallyStartRestStatistics(queueManagerActor)

    val shutdown = () => {
      implicit val ec: ExecutionContextExecutor = actorSystem.dispatcher

      val futureTerminationRestSQS = restServerOpt.map(_.stopAndGetFuture()).getOrElse(Future.unit)
      val futureTerminationRestStats = restStatisticsServerOpt.map(_.stopAndGetFuture()).getOrElse(Future.unit)
      val eventualTerminated = for {
        _ <- futureTerminationRestSQS
        _ <- futureTerminationRestStats
        ac <- actorSystem.terminate()
      } yield ac
      Await.result(eventualTerminated, Inf)

    }

    queueConfigStore match {
      case Some(queueConfigStoreActor) =>
        createQueues(queueConfigStoreActor, queueManagerActor) match {
          case Some(errors) =>
            errors.foreach(error => logger.error(s"Could not start server because $error"))
            shutdown()
          case None =>
        }
      case None =>
    }

    shutdown
  }

  private def createQueueEventListener: Option[ActorRef] =
    if (config.sqlQueuePersistenceConfig.enabled) {
      Some(
        actorSystem.actorOf(Props(new SqlQueuePersistenceActor(config.sqlQueuePersistenceConfig, config.baseQueues)))
      )
    } else if (config.queuesStorageEnabled) {
      Some(
        actorSystem.actorOf(Props(new ConfigBasedQueuePersistenceActor(config.queuesStoragePath, config.baseQueues)))
      )
    } else None

  private def createBase(queueConfigStore: Option[ActorRef]): ActorRef =
    actorSystem.actorOf(Props(new QueueManagerActor(new NowProvider(), config.restSqs.sqsLimits, queueConfigStore)))

  private def optionallyStartRestSqs(queueManagerActor: ActorRef, queueConfigStore: Option[ActorRef]): Option[SQSRestServer] = {
    if (config.restSqs.enabled) {

      val server = TheSQSRestServerBuilder(
        Some(actorSystem),
        Some(queueManagerActor),
        config.restSqs.bindHostname,
        config.restSqs.bindPort,
        config.nodeAddress,
        config.generateNodeAddress,
        config.restSqs.sqsLimits,
        config.awsRegion,
        config.awsAccountId,
        queueConfigStore
      ).start()

      server.waitUntilStarted()

      Some(server)
    } else {
      None
    }
  }

  private def optionallyStartRestStatistics(queueManagerActor: ActorRef): Option[StatisticsRestServer] = {
    if (config.restStatisticsConfiguration.enabled) {

      val server = TheStatisticsRestServerBuilder(
        actorSystem,
        queueManagerActor,
        config.restStatisticsConfiguration.bindHostname,
        config.restStatisticsConfiguration.bindPort,
        config.awsRegion,
        config.awsAccountId
      ).start()

      server.waitUntilStarted()

      Some(server)
    } else {
      None
    }
  }

  private def createQueues(queueConfigStore: ActorRef, queueManagerActor: ActorRef): Option[List[ElasticMQError]] = {
    implicit val timeout: Timeout = {
      import scala.concurrent.duration._
      Timeout(5.seconds)
    }

    Await.result(queueConfigStore ? Restore(queueManagerActor), timeout.duration).swap.toOption
  }
}
