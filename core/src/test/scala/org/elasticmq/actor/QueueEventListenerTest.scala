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
      case Right(queue) <- queueManagerActor ? CreateQueue(createQueueData("q1", MillisVisibilityTimeout(1L)))
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
      case Right(queue) <- queueManagerActor ? CreateQueue(createQueueData("q1", MillisVisibilityTimeout(1L)))
      _ <- queue ? SendMessage(createNewMessageData("abc", "xyz", Map.empty, MillisNextDelivery(100)))
    } yield {
      queueEventListener.expectMsgType[QueueEvent.QueueCreated].queue.name shouldBe "q1"
      queueEventListener.expectMsgType[QueueEvent.MessageAdded].message.id shouldBe "abc"
    }
  }

  test("QueueMessageUpdated event should be triggerred") {
    for {
      case Right(queue) <- queueManagerActor ? CreateQueue(createQueueData("q1", MillisVisibilityTimeout(1L)))
      _ <- queue ? SendMessage(createNewMessageData("abc", "xyz", Map.empty, MillisNextDelivery(100)))
      List(msg) <- queue ? ReceiveMessages(MillisVisibilityTimeout(1), 1, None, None)
      _ <- queue ? UpdateVisibilityTimeout(msg.deliveryReceipt.get, MillisVisibilityTimeout(2000))
    } yield {
      queueEventListener.expectMsgType[QueueEvent.QueueCreated].queue.name shouldBe "q1"
      queueEventListener.expectMsgType[QueueEvent.MessageAdded].message.id shouldBe "abc"
      queueEventListener.expectMsgType[QueueEvent.MessageUpdated].message.nextDelivery shouldBe 101 // receive
      queueEventListener.expectMsgType[QueueEvent.MessageUpdated].message.nextDelivery shouldBe 2100 // visibility
    }
  }

  test("QueueMessageUpdated event should carry a snapshot of the message, unaffected by later receives") {
    for {
      case Right(queue) <- queueManagerActor ? CreateQueue(createQueueData("q1", MillisVisibilityTimeout(1L)))
      _ <- queue ? SendMessage(createNewMessageData("abc", "xyz", Map.empty, MillisNextDelivery(1)))
      List(msg1) <- queue ? ReceiveMessages(MillisVisibilityTimeout(10000), 1, None, None)
      _ <- queue ? UpdateVisibilityTimeout(msg1.deliveryReceipt.get, MillisVisibilityTimeout(0))
      List(_) <- queue ? ReceiveMessages(MillisVisibilityTimeout(10000), 1, None, None)
    } yield {
      queueEventListener.expectMsgType[QueueEvent.QueueCreated]
      queueEventListener.expectMsgType[QueueEvent.MessageAdded]

      val afterFirstReceive = queueEventListener.expectMsgType[QueueEvent.MessageUpdated].message
      val afterVisibilityChange = queueEventListener.expectMsgType[QueueEvent.MessageUpdated].message
      val afterSecondReceive = queueEventListener.expectMsgType[QueueEvent.MessageUpdated].message

      afterFirstReceive.deliveryReceipts.size shouldBe 1
      afterFirstReceive.receiveCount shouldBe 1
      afterFirstReceive.nextDelivery shouldBe 10100

      afterVisibilityChange.deliveryReceipts.size shouldBe 1
      afterVisibilityChange.receiveCount shouldBe 1
      afterVisibilityChange.nextDelivery shouldBe 100

      afterSecondReceive.deliveryReceipts.size shouldBe 2
      afterSecondReceive.receiveCount shouldBe 2
    }
  }

  test("QueueMessageRemoved event should be triggerred") {
    for {
      case Right(queue) <- queueManagerActor ? CreateQueue(createQueueData("q1", MillisVisibilityTimeout(1L)))
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
