package org.elasticmq.actor

import org.elasticmq.actor.reply._
import org.elasticmq._
import org.elasticmq.msg._
import org.elasticmq.actor.test.{DataCreationHelpers, QueueManagerForEachTest, ActorTest}
import org.joda.time.DateTime

class QueueActorMsgOpsTest extends ActorTest with QueueManagerForEachTest with DataCreationHelpers {

  waitTest("non-existent msg should not be found") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)

      // When
      lookupResult <- queueActor ? LookupMessage(MessageId("xyz"))
    } yield {
      // Then
      lookupResult should be (None)
    }
  }

  waitTest("after persisting a msg it should be found") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val message = createNewMessageData("xyz", "123", MillisNextDelivery(123L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)
      _ <- queueActor ? SendMessage(message)

      // When
      lookupResult <- queueActor ? LookupMessage(MessageId("xyz"))
    } yield {
      // Then
      lookupResult.map(createNewMessageData(_)) should be (Some(message))
    }
  }

  waitTest("sending msg with maximum size should succeed") {
    // Given
    val maxMessageContent = "x" * 65535

    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val m = createNewMessageData("xyz", maxMessageContent, MillisNextDelivery(123L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)
      _ <- queueActor ? SendMessage(m)

      // When
      lookupResult <- queueActor ? LookupMessage(MessageId("xyz"))
    } yield {
      // Then
      lookupResult.map(createNewMessageData(_)) should be (Some(m))
    }
  }

  waitTest("no undelivered msg should not be found in an empty queue") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val q2 = createQueueData("q2", MillisVisibilityTimeout(2L))

    for {
      Right(queueActor1) <- queueManagerActor ? CreateQueue(q1)
      Right(queueActor2) <- queueManagerActor ? CreateQueue(q2)
      _ <- queueActor1 ? SendMessage(createNewMessageData("xyz", "123", MillisNextDelivery(123L)))

      // When
      lookupResult <- queueActor2 ? ReceiveMessages(1000L, DefaultVisibilityTimeout, 1)
    } yield {
      // Then
      lookupResult should be (Nil)
    }
  }

  waitTest("undelivered msg should be found in a non-empty queue") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val q2 = createQueueData("q2", MillisVisibilityTimeout(2L))
    val m = createNewMessageData("xyz", "123", MillisNextDelivery(123L))

    for {
      Right(queueActor1) <- queueManagerActor ? CreateQueue(q1)
      Right(queueActor2) <- queueManagerActor ? CreateQueue(q2)
      _ <- queueActor1 ? SendMessage(m)

      // When
      lookupResult <- queueActor1 ? ReceiveMessages(200L, DefaultVisibilityTimeout, 1)
    } yield {
      // Then
      withoutDeliveryReceipt(lookupResult.headOption).map(createNewMessageData(_)) should be (Some(m.copy(nextDelivery = MillisNextDelivery(101L))))
    }
  }

  waitTest("next delivery should be updated after receiving") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val m = createNewMessageData("xyz", "123", MillisNextDelivery(123L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)
      _ <- queueActor ? SendMessage(m)

      // When
      _ <- queueActor ? ReceiveMessages(200L, DefaultVisibilityTimeout, 1)
      lookupResult <- queueActor ? LookupMessage(MessageId("xyz"))
    } yield {
      // Then
      withoutDeliveryReceipt(lookupResult).map(createNewMessageData(_)) should be (Some(m.copy(nextDelivery = MillisNextDelivery(101L))))
    }
  }

  waitTest("receipt handle should be filled when receiving") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)

      _ <- queueActor ? SendMessage(createNewMessageData("xyz", "123", MillisNextDelivery(123L)))

      // When
      lookupBeforeReceiving <- queueActor ? LookupMessage(MessageId("xyz"))
      received <- queueActor ? ReceiveMessages(200L, DefaultVisibilityTimeout, 1)
      lookupAfterReceiving <- queueActor ? LookupMessage(MessageId("xyz"))
    } yield {
      // Then
      lookupBeforeReceiving.flatMap(_.deliveryReceipt) should be (None)

      val receivedReceipt = received.flatMap(_.deliveryReceipt)
      val lookedUpReceipt = lookupAfterReceiving.flatMap(_.deliveryReceipt)

      receivedReceipt.size  should be > (0)
      lookedUpReceipt should be ('defined)

      receivedReceipt.headOption should be (lookedUpReceipt)
    }
  }

  waitTest("receipt handle should change on subsequent receives") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)
      _ <- queueActor ? SendMessage(createNewMessageData("xyz", "123", MillisNextDelivery(100L)))

      // When
      received1 <- queueActor ? ReceiveMessages(200L, DefaultVisibilityTimeout, 1)
      received2 <- queueActor ? ReceiveMessages(400L, DefaultVisibilityTimeout, 1)
    } yield {
      // Then
      val received1Receipt = received1.flatMap(_.deliveryReceipt)
      val received2Receipt = received2.flatMap(_.deliveryReceipt)

      received1Receipt.size should be > (0)
      received2Receipt.size should be > (0)

      received1Receipt should not be (received2Receipt)
    }
  }

  waitTest("delivered msg should not be found in a non-empty queue when it is not visible") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)
      _ <- queueActor ? SendMessage(createNewMessageData("xyz", "123", MillisNextDelivery(123L)))

      // When
      receiveResult <- queueActor ? ReceiveMessages(100L, DefaultVisibilityTimeout, 1)
    } yield {
      // Then
      receiveResult should be (Nil)
    }
  }

  waitTest("increasing next delivery of a msg") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val m = createNewMessageData("xyz", "1234", MillisNextDelivery(123L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)
      _ <- queueActor ? SendMessage(m)

      // When
      _ <- queueActor ? UpdateVisibilityTimeout(m.id.get, MillisVisibilityTimeout(50L))
      lookupResult <- queueActor ? LookupMessage(MessageId("xyz"))
    } yield {
      // Then
      lookupResult.map(createNewMessageData(_)) should be (Some(createNewMessageData("xyz", "1234", MillisNextDelivery(150L))))
    }
  }

  waitTest("decreasing next delivery of a msg") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))   // Initially m2 should be delivered after m1
    val m1 = createNewMessageData("xyz1", "1234", MillisNextDelivery(150L))
    val m2 = createNewMessageData("xyz2", "1234", MillisNextDelivery(200L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)
      _ <- queueActor ? SendMessage(m1)
      _ <- queueActor ? SendMessage(m2)

      // When
      _ <- queueActor ? UpdateVisibilityTimeout(m2.id.get, MillisVisibilityTimeout(10L))
      receiveResult <- queueActor ? ReceiveMessages(120L, DefaultVisibilityTimeout, 1)
    } yield {
      // Then
      // This should find the first msg, as it has the visibility timeout decreased.
      receiveResult.headOption.map(_.id) should be (m2.id)
    }
  }

  waitTest("msg should be deleted") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val m1 = createNewMessageData("xyz", "123", MillisNextDelivery(123L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)
      _ <- queueActor ? SendMessage(m1)
      List(m1data) <- queueActor ? ReceiveMessages(200L, DefaultVisibilityTimeout, 1)

      // When
      _ <- queueActor ? DeleteMessage(m1data.deliveryReceipt.get)
      lookupResult <- queueActor ? LookupMessage(MessageId("xyz"))
    } yield {
      // Then
      lookupResult should be (None)
    }
  }

  waitTest("msg statistics should be updated") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val m1 = createNewMessageData("xyz", "123", MillisNextDelivery(100))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)
      _ <- queueActor ? SendMessage(m1)

      // When
      Some(lookupResult) <- queueActor ? LookupMessage(m1.id.get)
      List(receiveResult1) <- queueActor ? ReceiveMessages(100L, DefaultVisibilityTimeout, 1)
      List(receiveResult2) <- queueActor ? ReceiveMessages(200L, DefaultVisibilityTimeout, 1)
    } yield {
      // Then
      lookupResult.statistics should be (MessageStatistics(NeverReceived, 0))
      receiveResult1.statistics should be (MessageStatistics(OnDateTimeReceived(new DateTime(100L)), 1))
      receiveResult2.statistics should be (MessageStatistics(OnDateTimeReceived(new DateTime(200L)), 2))
    }
  }

  def withoutDeliveryReceipt(messageOpt: Option[MessageData]) = {
    messageOpt.map(_.copy(deliveryReceipt = None))
  }
}
