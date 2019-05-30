package org.elasticmq.actor.test

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{AsyncFunSuiteLike, BeforeAndAfterAll, Matchers}

import scala.concurrent.duration._

abstract class ActorTest
    extends TestKit(ActorSystem())
    with AsyncFunSuiteLike
    with ScalaFutures
    with Matchers
    with BeforeAndAfterAll {
  private val maxDuration = 1.minute
  implicit val timeout: Timeout = maxDuration
  implicit val ec = system.dispatcher

  override protected def afterAll(): Unit = {
    system.terminate().futureValue
    super.afterAll()
  }

}
