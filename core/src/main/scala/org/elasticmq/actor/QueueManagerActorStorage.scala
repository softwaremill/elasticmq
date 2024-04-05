package org.elasticmq.actor
import org.apache.pekko.actor.{ActorContext, ActorRef}
import org.apache.pekko.util.Timeout
import org.elasticmq.QueueData

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

trait QueueManagerActorStorage {

  def context: ActorContext

  implicit lazy val ec: ExecutionContext = context.dispatcher
  implicit lazy val timeout: Timeout = 5.seconds

  case class ActorWithQueueData(actorRef: ActorRef, queueData: QueueData)
  def queues: mutable.Map[String, ActorWithQueueData]
}
