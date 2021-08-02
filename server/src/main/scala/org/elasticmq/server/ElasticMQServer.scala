package org.elasticmq.server

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.actor.reply._
import org.elasticmq.rest.sqs.{CreateQueueDirectives, SQSRestServer, TheSQSRestServerBuilder}
import org.elasticmq.rest.stats.{StatisticsRestServer, TheStatisticsRestServerBuilder}
import org.elasticmq.server.config.{CreateQueue, ElasticMQServerConfig}
import org.elasticmq.util.{Logging, NowProvider}
import org.elasticmq.{DeadLettersQueueData, ElasticMQError, MillisVisibilityTimeout, QueueData}
import org.joda.time.{DateTime, Duration}

import scala.concurrent.duration.Duration.Inf
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

class ElasticMQServer(config: ElasticMQServerConfig) extends Logging {
  val actorSystem = ActorSystem("elasticmq")

  def start() = {
    val queueConfigStore: Option[ActorRef] = createQueueMetadataListener
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

    createQueues(queueManagerActor) match {
      case Nil => ()
      case errors =>
        errors.foreach(error => logger.error(s"Could not start server because $error"))
        shutdown()
    }

    shutdown
  }

  private def createQueueMetadataListener: Option[ActorRef] =
    if (config.queuesStorageEnabled) Some(actorSystem.actorOf(Props(new QueueMetadataListener(config.queuesStoragePath))))
    else None

  private def createBase(queueConfigStore: Option[ActorRef]): ActorRef = {
    config.storage match {
      case config.InMemoryStorage =>
        actorSystem.actorOf(Props(new QueueManagerActor(new NowProvider(), config.restSqs.sqsLimits, queueConfigStore)))
    }
  }

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

  private def createQueues(queueManagerActor: ActorRef): List[ElasticMQError] = {
    implicit val timeout = {
      import scala.concurrent.duration._
      Timeout(5.seconds)
    }

    val baseQueue: Seq[CreateQueue] = config.createBaseQueues
    val persistedQueues: Seq[CreateQueue] = config.createPersistedQueues(config.persistedQueuesConfig)
    (baseQueue ++ persistedQueues).distinct
      .flatMap(cq =>
        Await
          .result(queueManagerActor ? org.elasticmq.msg.CreateQueue(configToParams(cq, new DateTime)), timeout.duration)
          .swap
          .toOption
      ).toList
  }

  private def configToParams(cq: CreateQueue, now: DateTime): QueueData = {
    QueueData(
      name = cq.name,
      defaultVisibilityTimeout = MillisVisibilityTimeout.fromSeconds(
        cq.defaultVisibilityTimeoutSeconds.getOrElse(CreateQueueDirectives.DefaultVisibilityTimeout)
      ),
      delay = Duration.standardSeconds(cq.delaySeconds.getOrElse(CreateQueueDirectives.DefaultDelay)),
      receiveMessageWait = Duration.standardSeconds(
        cq.receiveMessageWaitSeconds.getOrElse(CreateQueueDirectives.DefaultReceiveMessageWait)
      ),
      created = now,
      lastModified = now,
      deadLettersQueue = cq.deadLettersQueue.map(dlq => DeadLettersQueueData(dlq.name, dlq.maxReceiveCount)),
      isFifo = cq.isFifo,
      hasContentBasedDeduplication = cq.hasContentBasedDeduplication,
      copyMessagesTo = cq.copyMessagesTo,
      moveMessagesTo = cq.moveMessagesTo,
      tags = cq.tags
    )
  }

}
