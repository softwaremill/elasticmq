package org.elasticmq.actor

import org.apache.pekko.actor.{ActorRef, Props}
import org.apache.pekko.testkit.{TestActor, TestProbe}
import org.elasticmq._
import org.elasticmq.actor.queue.{OperationSuccessful, QueueEvent}
import org.elasticmq.actor.reply._
import org.elasticmq.actor.test.{ActorTest, DataCreationHelpers}
import org.elasticmq.msg._
import org.elasticmq.util.MutableNowProvider

import java.time.Duration
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class MessageRetentionTest extends ActorTest with DataCreationHelpers {

  // Messages are created with the wall-clock time, so the test clock starts there
  private def newQueueManager(limits: Limits, listener: Option[ActorRef] = None) = {
    val nowProvider = new MutableNowProvider(System.currentTimeMillis())
    (nowProvider, system.actorOf(Props(new QueueManagerActor(nowProvider, limits, listener))))
  }

  private def createQueue(queueManager: ActorRef, retention: Option[Duration] = None): Future[ActorRef] =
    (queueManager ? CreateQueue(
      createQueueData("q-" + java.util.UUID.randomUUID(), MillisVisibilityTimeout(1L))
        .copy(messageRetentionPeriod = retention)
    )).map(_.toOption.get)

  private def send(queue: ActorRef, id: String) =
    queue ? SendMessage(createNewMessageData(id, "body", Map.empty, MillisNextDelivery(0L)))

  private def receive(queue: ActorRef) =
    (queue ? ReceiveMessages(MillisVisibilityTimeout(1L), 10, None, None)).map(_.map(_.id.id))

  test("with strict limits, messages expire after 4 days by default") {
    val (now, queueManager) = newQueueManager(StrictSQSLimits)
    for {
      queue <- createQueue(queueManager)
      _ <- send(queue, "m1")
      _ = now.mutableNowMillis.addAndGet(Duration.ofDays(4).minusSeconds(10).toMillis)
      beforeExpiry <- receive(queue)
      _ = now.mutableNowMillis.addAndGet(20.seconds.toMillis)
      afterExpiry <- receive(queue)
      stats <- queue ? GetQueueStatistics(now.nowMillis)
    } yield {
      beforeExpiry shouldBe List("m1")
      afterExpiry shouldBe Nil
      stats shouldBe QueueStatistics(0, 0, 0)
    }
  }

  test("with relaxed limits, messages don't expire by default") {
    val (now, queueManager) = newQueueManager(RelaxedSQSLimits)
    for {
      queue <- createQueue(queueManager)
      _ <- send(queue, "m1")
      _ = now.mutableNowMillis.addAndGet(Duration.ofDays(365).toMillis)
      received <- receive(queue)
    } yield received shouldBe List("m1")
  }

  test("a queue's retention period applies with both strict and relaxed limits") {
    val (now, queueManager) = newQueueManager(RelaxedSQSLimits)
    for {
      queue <- createQueue(queueManager, Some(Duration.ofSeconds(90)))
      _ <- send(queue, "m1")
      _ = now.mutableNowMillis.addAndGet(60.seconds.toMillis)
      beforeExpiry <- receive(queue)
      _ = now.mutableNowMillis.addAndGet(60.seconds.toMillis)
      afterExpiry <- receive(queue)
    } yield {
      beforeExpiry shouldBe List("m1")
      afterExpiry shouldBe Nil
    }
  }

  test("expired messages are not counted in the queue statistics") {
    val (now, queueManager) = newQueueManager(StrictSQSLimits)
    for {
      queue <- createQueue(queueManager, Some(Duration.ofSeconds(60)))
      _ <- send(queue, "m1")
      before <- queue ? GetQueueStatistics(now.nowMillis + 1000)
      _ = now.mutableNowMillis.addAndGet(61.seconds.toMillis)
      after <- queue ? GetQueueStatistics(now.nowMillis)
    } yield {
      before.approximateNumberOfVisibleMessages shouldBe 1
      after shouldBe QueueStatistics(0, 0, 0)
    }
  }

  test("a shorter retention period applies to messages already on the queue") {
    val (now, queueManager) = newQueueManager(StrictSQSLimits)
    for {
      queue <- createQueue(queueManager)
      _ <- send(queue, "m1")
      _ = now.mutableNowMillis.addAndGet(2.minutes.toMillis)
      _ <- queue ? UpdateQueueMessageRetentionPeriod(Duration.ofSeconds(60))
      received <- receive(queue)
    } yield received shouldBe Nil
  }

  test("expired messages are removed periodically and reported to the queue event listener") {
    val listener = TestProbe()
    listener.setAutoPilot((sender: ActorRef, msg: Any) => {
      msg match {
        case _: QueueEvent.MessageAdded | _: QueueEvent.MessageRemoved =>
          sender.tell(OperationSuccessful, listener.ref)
        case _ =>
      }
      TestActor.KeepRunning
    })
    val (now, queueManager) = newQueueManager(StrictSQSLimits, Some(listener.ref))
    for {
      queue <- createQueue(queueManager, Some(Duration.ofSeconds(60)))
      _ <- send(queue, "m1")
      _ = now.mutableNowMillis.addAndGet(61.seconds.toMillis)
    } yield {
      listener.fishForMessage(5.seconds) {
        case QueueEvent.MessageRemoved(_, "m1") => true
        case _                                  => false
      }
      succeed
    }
  }
}
