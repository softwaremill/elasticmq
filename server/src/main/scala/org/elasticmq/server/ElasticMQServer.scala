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
    val queueManagerActor = createBase()
    val restServerOpt = optionallyStartRestSqs(queueManagerActor)
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

  private def createBase(): ActorRef = {
    config.storage match {
      case config.InMemoryStorage =>
        actorSystem.actorOf(Props(new QueueManagerActor(new NowProvider(), config.restSqs.sqsLimits)))
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
        config.restSqs.sqsLimits,
        config.awsRegion,
        config.awsAccountId
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
        config.nodeAddress,
        config.generateNodeAddress,
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

    config.createQueues.flatMap(cq =>
      Await
        .result(queueManagerActor ? org.elasticmq.msg.CreateQueue(configToParams(cq, new DateTime)), timeout.duration)
        .swap
        .toOption
    )
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
