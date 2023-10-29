package org.elasticmq.metrics

import org.apache.pekko.actor.ActorRef
import org.apache.pekko.util.Timeout
import org.elasticmq.QueueStatistics
import org.elasticmq.actor.reply.ReplyActorRef
import org.elasticmq.msg.{GetQueueStatistics, ListQueues, LookupQueue}
import org.elasticmq.util.NowProvider

import scala.concurrent.{ExecutionContext, Future}

object QueueMetricsOps {

  def getQueueStatistics(queueName: String, queueManagerActor: ActorRef, nowProvider: NowProvider)(implicit
      timeout: Timeout,
      ec: ExecutionContext
  ): Future[QueueStatistics] = {
    for {
      maybeQueue <- queueManagerActor ? LookupQueue(queueName)
      queue = maybeQueue.getOrElse(throw new IllegalArgumentException(s"Could not find queue with name $queueName"))
      queueStatistics <- queue ? GetQueueStatistics(nowProvider.nowMillis)
    } yield queueStatistics
  }

  def getQueuesStatistics(queueManagerActor: ActorRef, nowProvider: NowProvider)(implicit
      timeout: Timeout,
      ec: ExecutionContext
  ): Future[Map[String, QueueStatistics]] = {
    for {
      queuesNames <- getQueueNames(queueManagerActor)
      statistics <- gatherAllStatistics(queuesNames, queueManagerActor, nowProvider)
    } yield statistics
  }

  def getQueueNames(queueManagerActor: ActorRef)(implicit timeout: Timeout): Future[Seq[String]] =
    queueManagerActor ? ListQueues()

  private def gatherAllStatistics(
      queueNames: Seq[String],
      queueManagerActor: ActorRef,
      nowProvider: NowProvider
  )(implicit timeout: Timeout, ec: ExecutionContext) = {
    val queuesStatistics = queueNames.map(queueName =>
      getQueueStatistics(queueName, queueManagerActor, nowProvider).map(statistics => (queueName, statistics))
    )
    Future.sequence(queuesStatistics).map(_.toMap)
  }
}
