package org.elasticmq.actor

import akka.testkit.TestKit
import akka.actor.ActorSystem
import org.scalatest.{FunSuite, BeforeAndAfterAll}
import org.scalatest.matchers.ShouldMatchers
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

abstract class ActorTest extends TestKit(ActorSystem()) with FunSuite with ShouldMatchers with BeforeAndAfterAll {
  private val maxDuration = 1.minute
  implicit val timeout: Timeout = maxDuration
  implicit val ec = system.dispatcher

  override protected def afterAll() {
    system.shutdown()
    super.afterAll()
  }

  def waitFor[T](f: Future[T]): T = Await.result(f, maxDuration)
}
