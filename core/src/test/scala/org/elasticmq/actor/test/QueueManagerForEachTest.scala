package org.elasticmq.actor.test

import org.apache.pekko.actor.{ActorRef, ActorSystem, Props}
import org.elasticmq.StrictSQSLimits
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.util.MutableNowProvider
import org.scalatest.{BeforeAndAfterEach, Suite}

trait QueueManagerForEachTest extends BeforeAndAfterEach {
  this: Suite =>

  val system: ActorSystem

  var queueManagerActor: ActorRef = _
  var nowProvider: MutableNowProvider = _

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    nowProvider = new MutableNowProvider
    queueManagerActor = system.actorOf(Props(new QueueManagerActor(nowProvider, StrictSQSLimits, None)))
  }

  override protected def afterEach(): Unit = {
    system.stop(queueManagerActor)
    super.afterEach()
  }
}
