package org.elasticmq.actor

import org.elasticmq.actor.reply._
import org.elasticmq._
import org.elasticmq.msg._
import org.elasticmq.actor.test.{ActorTest, DataCreationHelpers, QueueManagerForEachTest}
import org.joda.time.{DateTime, Duration}

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
      lookupResult should be(None)
    }
  }

  waitTest("after persisting a msg it should be found") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val message =
      createNewMessageData("xyz", "123", Map(), MillisNextDelivery(123L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)
      _ <- queueActor ? SendMessage(message)

      // When
      lookupResult <- queueActor ? LookupMessage(MessageId("xyz"))
    } yield {
      // Then
      lookupResult.map(createNewMessageData) should be(Some(message))
    }
  }

  waitTest("sending msg with maximum size should succeed") {
    // Given
    val maxMessageContent = "x" * 262143

    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val m = createNewMessageData("xyz", maxMessageContent, Map(), MillisNextDelivery(123L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)
      _ <- queueActor ? SendMessage(m)

      // When
      lookupResult <- queueActor ? LookupMessage(MessageId("xyz"))
    } yield {
      // Then
      lookupResult.map(createNewMessageData) should be(Some(m))
    }
  }

  waitTest("no undelivered msg should not be found in an empty queue") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val q2 = createQueueData("q2", MillisVisibilityTimeout(2L))

    for {
      Right(queueActor1) <- queueManagerActor ? CreateQueue(q1)
      Right(queueActor2) <- queueManagerActor ? CreateQueue(q2)
      _ <- queueActor1 ? SendMessage(createNewMessageData("xyz", "123", Map(), MillisNextDelivery(50L)))

      // When

      Right(lookupResult) <- queueActor2 ? ReceiveMessages(DefaultVisibilityTimeout, 1, None, None)
    } yield {
      // Then
      lookupResult should be(Nil)
    }
  }

  waitTest("undelivered msg should be found in a non-empty queue") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val q2 = createQueueData("q2", MillisVisibilityTimeout(2L))
    val m = createNewMessageData("xyz", "123", Map(), MillisNextDelivery(50L))

    for {
      Right(queueActor1) <- queueManagerActor ? CreateQueue(q1)
      Right(queueActor2) <- queueManagerActor ? CreateQueue(q2)
      _ <- queueActor1 ? SendMessage(m)

      // When
      Right(lookupResult) <- queueActor1 ? ReceiveMessages(DefaultVisibilityTimeout, 1, None, None)
    } yield {
      // Then
      withoutDeliveryReceipt(lookupResult.headOption)
        .map(createNewMessageData) should be(Some(m.copy(nextDelivery = MillisNextDelivery(101L))))
    }
  }

  waitTest("next delivery should be updated after receiving") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val m = createNewMessageData("xyz", "123", Map(), MillisNextDelivery(50L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)
      _ <- queueActor ? SendMessage(m)

      // When
      _ <- queueActor ? ReceiveMessages(DefaultVisibilityTimeout, 1, None, None)
      lookupResult <- queueActor ? LookupMessage(MessageId("xyz"))
    } yield {
      // Then
      withoutDeliveryReceipt(lookupResult).map(createNewMessageData) should be(
        Some(m.copy(nextDelivery = MillisNextDelivery(101L))))
    }
  }

  waitTest("receipt handle should be filled when receiving") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)

      _ <- queueActor ? SendMessage(createNewMessageData("xyz", "123", Map(), MillisNextDelivery(50L)))

      // When
      lookupBeforeReceiving <- queueActor ? LookupMessage(MessageId("xyz"))
      Right(received) <- queueActor ? ReceiveMessages(DefaultVisibilityTimeout, 1, None, None)
      lookupAfterReceiving <- queueActor ? LookupMessage(MessageId("xyz"))
    } yield {
      // Then
      lookupBeforeReceiving.flatMap(_.deliveryReceipt) should be(None)

      val receivedReceipt = received.flatMap(_.deliveryReceipt)
      val lookedUpReceipt = lookupAfterReceiving.flatMap(_.deliveryReceipt)

      receivedReceipt.size should be > (0)
      lookedUpReceipt should be('defined)

      receivedReceipt.headOption should be(lookedUpReceipt)
    }
  }

  waitTest("receipt handle should change on subsequent receives") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)
      _ <- queueActor ? SendMessage(createNewMessageData("xyz", "123", Map(), MillisNextDelivery(50L)))

      // When
      Right(received1) <- queueActor ? ReceiveMessages(DefaultVisibilityTimeout, 1, None, None)
      _ = nowProvider.mutableNowMillis.set(101L)
      Right(received2) <- queueActor ? ReceiveMessages(DefaultVisibilityTimeout, 1, None, None)
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
    val q1 = createQueueData("q1", MillisVisibilityTimeout(100L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)
      _ <- queueActor ? SendMessage(createNewMessageData("xyz", "123", Map(), MillisNextDelivery(123L)))

      // When
      Right(receiveResult) <- queueActor ? ReceiveMessages(DefaultVisibilityTimeout, 1, None, None)
    } yield {
      // Then
      receiveResult should be(Nil)
    }
  }

  waitTest("increasing next delivery of a msg") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val m = createNewMessageData("xyz", "1234", Map(), MillisNextDelivery(123L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)
      _ <- queueActor ? SendMessage(m)

      // When
      _ <- queueActor ? UpdateVisibilityTimeout(m.id.get, MillisVisibilityTimeout(50L))
      lookupResult <- queueActor ? LookupMessage(MessageId("xyz"))
    } yield {
      // Then
      lookupResult.map(createNewMessageData) should be(
        Some(createNewMessageData("xyz", "1234", Map(), MillisNextDelivery(150L))))
    }
  }

  waitTest("decreasing next delivery of a msg") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L)) // Initially m2 should be delivered after m1
    val m1 =
      createNewMessageData("xyz1", "1234", Map(), MillisNextDelivery(150L))
    val m2 =
      createNewMessageData("xyz2", "1234", Map(), MillisNextDelivery(200L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)
      _ <- queueActor ? SendMessage(m1)
      _ <- queueActor ? SendMessage(m2)

      // When
      _ <- queueActor ? UpdateVisibilityTimeout(m2.id.get, MillisVisibilityTimeout(10L))
      _ = nowProvider.mutableNowMillis.set(110L)
      Right(receiveResult) <- queueActor ? ReceiveMessages(DefaultVisibilityTimeout, 1, None, None)
    } yield {
      // Then
      // This should find the first msg, as it has the visibility timeout decreased.
      receiveResult.headOption.map(_.id) should be(m2.id)
    }
  }

  waitTest("msg should be deleted") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val m1 = createNewMessageData("xyz", "123", Map(), MillisNextDelivery(50L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)
      _ <- queueActor ? SendMessage(m1)
      Right(List(m1data)) <- queueActor ? ReceiveMessages(DefaultVisibilityTimeout, 1, None, None)

      // When
      _ <- queueActor ? DeleteMessage(m1data.deliveryReceipt.get)
      lookupResult <- queueActor ? LookupMessage(MessageId("xyz"))
    } yield {
      // Then
      lookupResult should be(None)
    }
  }

  waitTest("msg statistics should be updated") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val m1 = createNewMessageData("xyz", "123", Map(), MillisNextDelivery(50L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)
      _ <- queueActor ? SendMessage(m1)

      // When
      Some(lookupResult) <- queueActor ? LookupMessage(m1.id.get)
      Right(List(receiveResult1)) <- queueActor ? ReceiveMessages(DefaultVisibilityTimeout, 1, None, None)
      _ = nowProvider.mutableNowMillis.set(110L)
      Right(List(receiveResult2)) <- queueActor ? ReceiveMessages(DefaultVisibilityTimeout, 1, None, None)
    } yield {
      // Then
      lookupResult.statistics should be(MessageStatistics.empty)
      receiveResult1.statistics should be(MessageStatistics(OnDateTimeReceived(new DateTime(100L)), 1))
      receiveResult2.statistics should be(MessageStatistics(OnDateTimeReceived(new DateTime(100L)), 2))
    }
  }

  waitTest("should receive at most as much messages as given") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val msgs = (for { i <- 1 to 5 } yield createNewMessageData("xyz" + i, "123", Map(), MillisNextDelivery(i))).toList
    val List(m1, m2, m3, m4, m5) = msgs

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)
      _ <- queueActor ? SendMessage(m1)
      _ <- queueActor ? SendMessage(m2)
      _ <- queueActor ? SendMessage(m3)
      _ <- queueActor ? SendMessage(m4)
      _ <- queueActor ? SendMessage(m5)

      // When
      Right(receiveResults1) <- queueActor ? ReceiveMessages(DefaultVisibilityTimeout, 3, None, None)
      Right(receiveResults2) <- queueActor ? ReceiveMessages(DefaultVisibilityTimeout, 2, None, None)
    } yield {
      // Then
      receiveResults1.size should be(3)
      receiveResults2.size should be(2)

      (receiveResults1.map(_.id.id) ++ receiveResults2.map(_.id.id)) should be(msgs.map(_.id.get.id))
    }
  }

  waitTest("should receive as much messages as possible") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val msgs = (for { i <- 1 to 3 } yield createNewMessageData("xyz" + i, "123", Map(), MillisNextDelivery(i))).toList
    val List(m1, m2, m3) = msgs

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)
      _ <- queueActor ? SendMessage(m1)
      _ <- queueActor ? SendMessage(m2)
      _ <- queueActor ? SendMessage(m3)

      // When
      Right(receiveResults) <- queueActor ? ReceiveMessages(DefaultVisibilityTimeout, 5, None, None)
    } yield {
      // Then
      receiveResults.size should be(3)

      receiveResults.map(_.id.id) should be(msgs.map(_.id.get.id))
    }
  }

  waitTest("should wait for messages to be received for the specified period of time") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val start = System.currentTimeMillis()

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)

      // When
      Right(receiveResults) <- queueActor ? ReceiveMessages(DefaultVisibilityTimeout,
                                                            5,
                                                            Some(Duration.millis(500L)),
                                                            None)
    } yield {
      // Then
      val end = System.currentTimeMillis()
      (end - start) should be >= (500L)

      receiveResults should be(Nil)
    }
  }

  waitTest("should wait until messages are available") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val msg =
      createNewMessageData("xyz", "123", Map(), MillisNextDelivery(200L))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)

      // When
      receiveResultsFuture = queueActor ? ReceiveMessages(DefaultVisibilityTimeout,
                                                          5,
                                                          Some(Duration.millis(1000L)),
                                                          None)
      _ <- {
        Thread.sleep(500); nowProvider.mutableNowMillis.set(200L)
        queueActor ? SendMessage(msg)
      }

      Right(receiveResults) <- receiveResultsFuture
    } yield {
      // Then
      receiveResults.size should be(1)
      receiveResults.map(_.id) should be(msg.id.toList)
    }
  }

  waitTest("multiple futures should wait until messages are available, and receive the message only once") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val msg = createNewMessageData("xyz", "123", Map(), MillisNextDelivery(100))
    val start = System.currentTimeMillis()

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)

      // When
      receiveResults1Future = queueActor ? ReceiveMessages(DefaultVisibilityTimeout,
                                                           5,
                                                           Some(Duration.millis(1000L)),
                                                           None)
      receiveResults2Future = queueActor ? ReceiveMessages(DefaultVisibilityTimeout,
                                                           5,
                                                           Some(Duration.millis(1000L)),
                                                           None)

      _ <- { Thread.sleep(500); queueActor ? SendMessage(msg) }

      Right(receiveResults1) <- receiveResults1Future
      Right(receiveResults2) <- receiveResults2Future
    } yield {
      // Then
      val end = System.currentTimeMillis()
      (end - start) should be >= (1000L) // no reply for one of the futures

      Set(receiveResults1.size, receiveResults2.size) should be(Set(0, 1))
      (receiveResults1 ++ receiveResults2).map(_.id) should be(msg.id.toList)
    }
  }

  waitTest("multiple futures should wait until messages are available, and receive all sent messages") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(1L))
    val msg1 =
      createNewMessageData("xyz1", "123a", Map(), MillisNextDelivery(100))
    val msg2 =
      createNewMessageData("xyz2", "123b", Map(), MillisNextDelivery(100))

    for {
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)

      // When
      receiveResults1Future = queueActor ? ReceiveMessages(DefaultVisibilityTimeout,
                                                           5,
                                                           Some(Duration.millis(1000L)),
                                                           None)
      receiveResults2Future = queueActor ? ReceiveMessages(DefaultVisibilityTimeout,
                                                           5,
                                                           Some(Duration.millis(1000L)),
                                                           None)
      receiveResults3Future = queueActor ? ReceiveMessages(DefaultVisibilityTimeout,
                                                           5,
                                                           Some(Duration.millis(1000L)),
                                                           None)

      _ <- {
        Thread.sleep(500); queueActor ? SendMessage(msg1)
        queueActor ? SendMessage(msg2)
      }

      Right(receiveResults1) <- receiveResults1Future
      Right(receiveResults2) <- receiveResults2Future
      Right(receiveResults3) <- receiveResults3Future
    } yield {
      // Then
      List(receiveResults1.size, receiveResults2.size, receiveResults3.size).sum should be(2)
      (receiveResults1 ++ receiveResults2 ++ receiveResults3)
        .map(_.id)
        .toSet should be((msg1.id.toList ++ msg2.id.toList).toSet)
    }
  }

  waitTest("should send unprocessed messages to dead letters queue and delete from original") {
    // Given
    val deadLettersQueueName = "dlq1"
    val m1ID = "xyz"
    val dlq1 =
      createQueueData(deadLettersQueueName, MillisVisibilityTimeout(1L), None)

    val q1 =
      createQueueData("q1", MillisVisibilityTimeout(1L), Some(DeadLettersQueueData(deadLettersQueueName, 1)))
    val m1 = createNewMessageData(m1ID, "123", Map(), MillisNextDelivery(100))

    for {
      Right(deadLettersQueueActor) <- queueManagerActor ? CreateQueue(dlq1)
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)
      _ <- queueActor ? SendMessage(m1)

      // When
      Right(receiveResults) <- queueActor ? ReceiveMessages(DefaultVisibilityTimeout, 5, None, None)
      _ = nowProvider.mutableNowMillis.set(1000L)
      Right(receiveResultsEmpty) <- queueActor ? ReceiveMessages(DefaultVisibilityTimeout, 5, None, None)
      Right(receiveResultsDeadLettersQueue) <- deadLettersQueueActor ? ReceiveMessages(DefaultVisibilityTimeout,
                                                                                       5,
                                                                                       None,
                                                                                       None)
    } yield {
      // Then
      receiveResults.size should be(1)
      receiveResultsEmpty.size should be(0)
      receiveResultsDeadLettersQueue.size should be(1)

      receiveResults.head.id.id should be(m1ID)
      receiveResultsDeadLettersQueue.head.id.id should be(m1ID)
    }
  }

  waitTest(
    "should send unprocessed messages to dead letters queue and delete from original when dlq is added after creation") {
    // Given
    val deadLettersQueueName = "dlq11"
    val m1ID = "xyz"
    val dlq1 =
      createQueueData(deadLettersQueueName, MillisVisibilityTimeout(1L), None)

    val q1 =
      createQueueData("q11", MillisVisibilityTimeout(1L))
    val m1 = createNewMessageData(m1ID, "123", Map(), MillisNextDelivery(100))

    for {
      Right(deadLettersQueueActor) <- queueManagerActor ? CreateQueue(dlq1)
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)
      _ <- queueActor ? UpdateQueueDeadLettersQueue(Some(DeadLettersQueueData(deadLettersQueueName, 1)),
                                                    Some(deadLettersQueueActor))
      _ <- queueActor ? SendMessage(m1)

      // When
      Right(receiveResults) <- queueActor ? ReceiveMessages(DefaultVisibilityTimeout, 5, None, None)
      _ = nowProvider.mutableNowMillis.set(1000L)
      Right(receiveResultsEmpty) <- queueActor ? ReceiveMessages(DefaultVisibilityTimeout, 5, None, None)
      Right(receiveResultsDeadLettersQueue) <- deadLettersQueueActor ? ReceiveMessages(DefaultVisibilityTimeout,
                                                                                       5,
                                                                                       None,
                                                                                       None)
    } yield {
      // Then
      receiveResults.size should be(1)
      receiveResultsEmpty.size should be(0)
      receiveResultsDeadLettersQueue.size should be(1)

      receiveResults.head.id.id should be(m1ID)
      receiveResultsDeadLettersQueue.head.id.id should be(m1ID)
    }
  }

  waitTest("should move messages to move-to queue when configured") {
    // Given
    val redirectToQueue = createQueueData("redirectTo", MillisVisibilityTimeout(1L))
    val q1 = createQueueData("queue1", MillisVisibilityTimeout(1L), moveMessagesToQueue = Some("redirectTo"))
    val m1ID = "xyz"
    val m1 = createNewMessageData(m1ID, "123", Map(), MillisNextDelivery(100))

    for {
      Right(redirectToActor) <- queueManagerActor ? CreateQueue(redirectToQueue)
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)

      // When
      sendAck <- queueActor ? SendMessage(m1)
      _ = nowProvider.mutableNowMillis.set(1000L)
      Right(receiveResultsOriginal) <- queueActor ? ReceiveMessages(DefaultVisibilityTimeout, 5, None, None)
      Right(receiveResultsMoved) <- redirectToActor ? ReceiveMessages(DefaultVisibilityTimeout, 5, None, None)
    } yield {
      // Then
      sendAck.id should be(MessageId(m1ID))
      receiveResultsOriginal.size should be(0)
      receiveResultsMoved.map(_.id) should be(List(MessageId(m1ID)))
    }
  }

  waitTest("should copy messages to copy-to queue when configured") {
    // Given
    val copyToQueue = createQueueData("copyTo", MillisVisibilityTimeout(1L))
    val q1 = createQueueData("queue1", MillisVisibilityTimeout(1L), copyMessagesToQueue = Some("copyTo"))
    val m1ID = "xyz"
    val m1 = createNewMessageData(m1ID, "123", Map(), MillisNextDelivery(100))

    for {
      Right(copyToActor) <- queueManagerActor ? CreateQueue(copyToQueue)
      Right(queueActor) <- queueManagerActor ? CreateQueue(q1)

      // When
      _ <- queueActor ? SendMessage(m1)
      _ = nowProvider.mutableNowMillis.set(1000L)
      Right(resultsOriginal) <- queueActor ? ReceiveMessages(DefaultVisibilityTimeout, 5, None, None)
      Right(resultsCopied) <- copyToActor ? ReceiveMessages(DefaultVisibilityTimeout,
                                                            5,
                                                            Some(Duration.millis(500)),
                                                            None)
    } yield {
      // Then
      resultsOriginal.map(_.id) should be(List(MessageId(m1ID)))
      resultsCopied.map(_.id) should be(List(MessageId(m1ID)))
    }
  }

  def withoutDeliveryReceipt(messageOpt: Option[MessageData]) = {
    messageOpt.map(_.copy(deliveryReceipt = None))
  }
}
