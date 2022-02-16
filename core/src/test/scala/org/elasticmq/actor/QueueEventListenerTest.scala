package org.elasticmq.actor

import org.elasticmq.actor.queue._
import org.elasticmq.actor.reply._
import org.elasticmq.actor.test.{ActorTest, DataCreationHelpers, QueueManagerWithListenerForEachTest}
import org.elasticmq.msg._
import org.elasticmq.{MillisNextDelivery, MillisVisibilityTimeout}

class QueueEventListenerTest extends ActorTest with QueueManagerWithListenerForEachTest with DataCreationHelpers {

  test("QueueCreated event should be triggerred") {
    for {
      _ <- queueManagerActor ? CreateQueue(createQueueData("q1", MillisVisibilityTimeout(1L)))
    } yield {
      queueEventListener.expectMsgType[QueueEvent.QueueCreated].queue.name shouldBe "q1"
    }
  }

  test("QueueDeleted event should be triggerred") {
    for {
      _ <- queueManagerActor ? CreateQueue(createQueueData("q1", MillisVisibilityTimeout(1L)))
      _ <- queueManagerActor ? DeleteQueue("q1")
    } yield {
      queueEventListener.expectMsgType[QueueEvent.QueueCreated].queue.name shouldBe "q1"
      queueEventListener.expectMsgType[QueueEvent.QueueDeleted].queueName shouldBe "q1"
    }
  }

  test("QueueMetadataUpdated event should be triggerred") {
    for {
      Right(queue) <- queueManagerActor ? CreateQueue(createQueueData("q1", MillisVisibilityTimeout(1L)))
      _ <- queue ? UpdateQueueDefaultVisibilityTimeout(MillisVisibilityTimeout(1000))
    } yield {
      queueEventListener.expectMsgType[QueueEvent.QueueCreated].queue.name shouldBe "q1"
      queueEventListener
        .expectMsgType[QueueEvent.QueueMetadataUpdated]
        .queue
        .defaultVisibilityTimeout
        .millis shouldBe 1000
    }
  }

  test("QueueMessageAdded event should be triggerred") {
    for {
      Right(queue) <- queueManagerActor ? CreateQueue(createQueueData("q1", MillisVisibilityTimeout(1L)))
      _ <- queue ? SendMessage(createNewMessageData("abc", "xyz", Map.empty, MillisNextDelivery(100)))
    } yield {
      queueEventListener.expectMsgType[QueueEvent.QueueCreated].queue.name shouldBe "q1"
      queueEventListener.expectMsgType[QueueEvent.MessageAdded].message.id shouldBe "abc"
    }
  }

  test("QueueMessageUpdated event should be triggerred") {
    for {
      Right(queue) <- queueManagerActor ? CreateQueue(createQueueData("q1", MillisVisibilityTimeout(1L)))
      _ <- queue ? SendMessage(createNewMessageData("abc", "xyz", Map.empty, MillisNextDelivery(100)))
      List(msg) <- queue ? ReceiveMessages(MillisVisibilityTimeout(1), 1, None, None)
      _ <- queue ? UpdateVisibilityTimeout(msg.deliveryReceipt.get, MillisVisibilityTimeout(2000))
    } yield {
      queueEventListener.expectMsgType[QueueEvent.QueueCreated].queue.name shouldBe "q1"
      queueEventListener.expectMsgType[QueueEvent.MessageAdded].message.id shouldBe "abc"
      queueEventListener.expectMsgType[QueueEvent.MessageUpdated].message.nextDelivery shouldBe 2100
    }
  }

  test("QueueMessageRemoved event should be triggerred") {
    for {
      Right(queue) <- queueManagerActor ? CreateQueue(createQueueData("q1", MillisVisibilityTimeout(1L)))
      _ <- queue ? SendMessage(createNewMessageData("abc", "xyz", Map.empty, MillisNextDelivery(1)))
      List(msg) <- queue ? ReceiveMessages(MillisVisibilityTimeout(1), 1, None, None)
      _ <- queue ? DeleteMessage(msg.deliveryReceipt.get)
    } yield {
      queueEventListener.expectMsgType[QueueEvent.QueueCreated].queue.name shouldBe "q1"
      queueEventListener.expectMsgType[QueueEvent.MessageAdded].message.id shouldBe "abc"
      queueEventListener.expectMsgType[QueueEvent.MessageUpdated].message.deliveryReceipts.size shouldBe 1
      queueEventListener.expectMsgType[QueueEvent.MessageRemoved].messageId shouldBe "abc"
    }
  }
}
