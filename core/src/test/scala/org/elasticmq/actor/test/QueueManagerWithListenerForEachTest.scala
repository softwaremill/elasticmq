package org.elasticmq.actor.test

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{TestActor, TestActors, TestProbe}
import org.elasticmq.StrictSQSLimits
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.actor.queue.{OperationSuccessful, QueueEvent}
import org.elasticmq.util.MutableNowProvider
import org.scalatest.{BeforeAndAfterEach, Suite}

trait QueueManagerWithListenerForEachTest extends BeforeAndAfterEach {
  this: Suite =>

  val system: ActorSystem

  var queueEventListener: TestProbe = _
  var queueManagerActor: ActorRef = _
  var nowProvider: MutableNowProvider = _

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    nowProvider = new MutableNowProvider

    queueEventListener = new TestProbe(system)
    queueEventListener.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case QueueEvent.MessageAdded(_, _)   => sender.tell(OperationSuccessful, queueEventListener.ref)
        case QueueEvent.MessageUpdated(_, _) => sender.tell(OperationSuccessful, queueEventListener.ref)
        case QueueEvent.MessageRemoved(_, _) => sender.tell(OperationSuccessful, queueEventListener.ref)
        case _                               =>
      }
      TestActor.KeepRunning
    })

    queueManagerActor =
      system.actorOf(Props(new QueueManagerActor(nowProvider, StrictSQSLimits, Some(queueEventListener.ref))))
  }

  override protected def afterEach(): Unit = {
    system.stop(queueManagerActor)
    super.afterEach()
  }
}
