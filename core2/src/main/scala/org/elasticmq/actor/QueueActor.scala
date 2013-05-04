package org.elasticmq.actor

import akka.actor.Actor
import org.elasticmq.data.QueueData

class QueueActor(initialQueueData: QueueData) extends Actor {
  private var queueData = initialQueueData

  def receive = {
    case _ =>
  }
}
