package org.elasticmq.actor.test

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.TestKit
import org.apache.pekko.util.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import org.scalatest.funsuite.AsyncFunSuiteLike
import org.scalatest.matchers.should.Matchers

abstract class ActorTest
    extends TestKit(ActorSystem())
    with AsyncFunSuiteLike
    with ScalaFutures
    with Matchers
    with BeforeAndAfterAll {
  private val maxDuration = 1.minute
  implicit val timeout: Timeout = maxDuration
  implicit val ec: ExecutionContext = system.dispatcher

  override protected def afterAll(): Unit = {
    system.terminate().futureValue
    super.afterAll()
  }

}
