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
      queueEventListener.expectMsgType[QueueCreated].queue.name shouldBe "q1"
    }
  }

  test("QueueDeleted event should be triggerred") {
    for {
      _ <- queueManagerActor ? CreateQueue(createQueueData("q1", MillisVisibilityTimeout(1L)))
      _ <- queueManagerActor ? DeleteQueue("q1")
    } yield {
      queueEventListener.expectMsgType[QueueCreated].queue.name shouldBe "q1"
      queueEventListener.expectMsgType[QueueDeleted].queueName shouldBe "q1"
    }
  }

  test("QueueMetadataUpdated event should be triggerred") {
    for {
      Right(queue) <- queueManagerActor ? CreateQueue(createQueueData("q1", MillisVisibilityTimeout(1L)))
      _ <- queue ? UpdateQueueDefaultVisibilityTimeout(MillisVisibilityTimeout(1000))
    } yield {
      queueEventListener.expectMsgType[QueueCreated].queue.name shouldBe "q1"
      queueEventListener.expectMsgType[QueueMetadataUpdated].queue.defaultVisibilityTimeout.millis shouldBe 1000
    }
  }

  test("QueueMessageAdded event should be triggerred") {
    for {
      Right(queue) <- queueManagerActor ? CreateQueue(createQueueData("q1", MillisVisibilityTimeout(1L)))
      _ <- queue ? SendMessage(createNewMessageData("abc", "xyz", Map.empty, MillisNextDelivery(100)))
    } yield {
      queueEventListener.expectMsgType[QueueCreated].queue.name shouldBe "q1"
      queueEventListener.expectMsgType[QueueMessageAdded].message.id shouldBe "abc"
    }
  }

  test("QueueMessageUpdated event should be triggerred") {
    for {
      Right(queue) <- queueManagerActor ? CreateQueue(createQueueData("q1", MillisVisibilityTimeout(1L)))
      msg <- queue ? SendMessage(createNewMessageData("abc", "xyz", Map.empty, MillisNextDelivery(100)))
      _ <- queue ? UpdateVisibilityTimeout(msg.id, MillisVisibilityTimeout(2000))
    } yield {
      queueEventListener.expectMsgType[QueueCreated].queue.name shouldBe "q1"
      queueEventListener.expectMsgType[QueueMessageAdded].message.id shouldBe "abc"
      queueEventListener.expectMsgType[QueueMessageUpdated].message.nextDelivery shouldBe 2100
    }
  }

  test("QueueMessageRemoved event should be triggerred") {
    for {
      Right(queue) <- queueManagerActor ? CreateQueue(createQueueData("q1", MillisVisibilityTimeout(1L)))
      _ <- queue ? SendMessage(createNewMessageData("abc", "xyz", Map.empty, MillisNextDelivery(1)))
      List(msg) <- queue ? ReceiveMessages(MillisVisibilityTimeout(1), 1, None, None)
      _ <- queue ? DeleteMessage(msg.deliveryReceipt.get)
    } yield {
      queueEventListener.expectMsgType[QueueCreated].queue.name shouldBe "q1"
      queueEventListener.expectMsgType[QueueMessageAdded].message.id shouldBe "abc"
      queueEventListener.expectMsgType[QueueMessageUpdated].message.deliveryReceipts.size shouldBe 1
      queueEventListener.expectMsgType[QueueMessageRemoved].messageId shouldBe "abc"
    }
  }
}
