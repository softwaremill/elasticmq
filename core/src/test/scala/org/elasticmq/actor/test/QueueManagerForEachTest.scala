package org.elasticmq.actor.test

import akka.actor.{ActorRef, ActorSystem, Props}
import org.elasticmq.{MessagePersistenceConfig, StrictSQSLimits}
import org.elasticmq.actor.QueueManagerActor
import org.elasticmq.util.MutableNowProvider
import org.scalatest.{BeforeAndAfterEach, Suite}

trait QueueManagerForEachTest extends BeforeAndAfterEach {
  this: Suite =>

  val system: ActorSystem

  var queueManagerActor: ActorRef = _
  var nowProvider: MutableNowProvider = _

  def messagePersistenceConfig: MessagePersistenceConfig

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    nowProvider = new MutableNowProvider
    queueManagerActor = system.actorOf(Props(new QueueManagerActor(nowProvider, StrictSQSLimits, messagePersistenceConfig, None)))
  }

  override protected def afterEach(): Unit = {
    system.stop(queueManagerActor)
    super.afterEach()
  }
}

trait MessagePersistenceDisabledConfig {
  def messagePersistenceConfig: MessagePersistenceConfig = MessagePersistenceConfig()
}

trait MessagePersistenceEnabledConfig {
  def messagePersistenceConfig: MessagePersistenceConfig = MessagePersistenceConfig(
    enabled = true,
    driverClass = "org.sqlite.JDBC",
    uri = "jdbc:sqlite:./elastimq.db",
    pruneDataOnInit = true)
}
