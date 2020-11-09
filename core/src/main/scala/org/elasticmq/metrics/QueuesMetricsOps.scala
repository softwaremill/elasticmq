package org.elasticmq.metrics

import akka.actor.ActorRef
import akka.util.Timeout
import org.elasticmq.QueueStatistics
import org.elasticmq.actor.reply.ReplyActorRef
import org.elasticmq.msg.{DeleteQueue, GetQueueStatistics, ListQueues, LookupQueue}
import org.elasticmq.util.NowProvider

import scala.concurrent.{ExecutionContext, Future}

object QueuesMetricsOps {

  def getQueueNames(queueManagerActor: ActorRef)(implicit timeout: Timeout): Future[Seq[String]] = queueManagerActor ? ListQueues()

  def deleteQueue(queueName: String, queueManagerActor: ActorRef)(implicit timeout: Timeout): Future[Unit] = queueManagerActor ? DeleteQueue(queueName)

}

object QueueMetricsOps {

  def getNumberOfMessagesInQueue(queueName: String, queueManagerActor: ActorRef, nowProvider: NowProvider)(implicit timeout: Timeout, ec: ExecutionContext): Future[QueueStatistics] = {
    for {
      maybeQueue <- queueManagerActor ? LookupQueue(queueName)
      queue = maybeQueue.getOrElse(throw new RuntimeException("not found queue"))
      queueStatistics <- queue ? GetQueueStatistics(nowProvider.nowMillis)
    } yield queueStatistics
  }

  def getNumberOfMessagesInAllQueues(queueManagerActor: ActorRef, nowProvider: NowProvider)(implicit timeout: Timeout, ec: ExecutionContext): Future[Map[String, QueueStatistics]] = {
    for {
      queuesNames <- QueuesMetricsOps.getQueueNames(queueManagerActor)
      statistics <- getAllStatistics(queuesNames, queueManagerActor, nowProvider)
    } yield statistics
  }

  private def getAllStatistics(queueNames: Seq[String], queueManagerActor: ActorRef, nowProvider: NowProvider)(implicit timeout: Timeout, ec: ExecutionContext) = {
    val queuesStatistics = queueNames.map(queueName => getNumberOfMessagesInQueue(queueName, queueManagerActor, nowProvider).map(statistics => (queueName, statistics)))
    Future.sequence(queuesStatistics).map(_.toMap)
  }
}