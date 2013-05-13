package org.elasticmq.actor.test

import org.scalatest.{Suite, BeforeAndAfterEach}
import akka.actor.{Props, ActorSystem, ActorRef}
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.util.NowProvider

trait QueueManagerForEachTest extends BeforeAndAfterEach {
  this: Suite =>

  val system: ActorSystem

  var queueManagerActor: ActorRef = _
  var nowProvider: NowProvider = _

  override protected def beforeEach() {
    super.beforeEach()
    nowProvider = new MutableNowProvider
    queueManagerActor = system.actorOf(Props(new QueueManagerActor(nowProvider)))
  }

  override protected def afterEach() {
    system.stop(queueManagerActor)
    super.afterEach()
  }
}
