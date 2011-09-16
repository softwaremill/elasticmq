package org.elasticmq.impl

import org.scalatest.matchers.MustMatchers
import org.scalatest._

import mock.MockitoSugar
import org.mockito.Mockito.{when, verify}
import org.mockito.Matchers._
import org.mockito.Matchers.{eq => musteq}
import org.elasticmq._
import org.mockito.ArgumentMatcher
import org.elasticmq.storage.{MessageStorageModule, QueueStorageModule}

class NativeMessageClientImplTestSuite extends FunSuite with MustMatchers with MockitoSugar {
  val NOW = 1316168602L

  test("sending a message should generate an id and properly set the next delivery") {
    // Given
    val (messageClient, mockStorage) = createMessageClientWithMockStorage
    val q1 = Queue("q1", VisibilityTimeout(123L))

    // When
    val msg = messageClient.sendMessage(Message(q1, "abc"))

    // Then
    val expectedNextDelivery = NOW + q1.defaultVisibilityTimeout.millis
    verify(mockStorage).persistMessage(argThat(new ArgumentMatcher[SpecifiedMessage]{
      def matches(msgRef: AnyRef) = msgRef.asInstanceOf[SpecifiedMessage].nextDelivery.millis == expectedNextDelivery
    }))
    msg.nextDelivery must be (MillisNextDelivery(expectedNextDelivery))
  }

  test("receiving a message should return None if there are no pending messages") {
    // Given
    val (messageClient, mockStorage) = createMessageClientWithMockStorage
    val q1 = Queue("q1", VisibilityTimeout(123L))

    when(mockStorage.lookupPendingMessage(musteq(q1), anyLong())).thenReturn(None)

    // When
    val result = messageClient.receiveMessage(q1)

    // Then
    result must be (None)
  }

  test("receiving a message should return a message if there is a pending message") {
    // Given
    val (messageClient, mockStorage) = createMessageClientWithMockStorage
    val q1 = Queue("q1", VisibilityTimeout(123L))
    val m1 = Message(q1, "id1", "abc", MillisNextDelivery(NOW + 123L))

    when(mockStorage.lookupPendingMessage(musteq(q1), anyLong())).thenReturn(Some(m1))
    when(mockStorage.updateNextDelivery(musteq(m1), any(classOf[MillisNextDelivery]))).thenReturn(Some(m1))

    // When
    val result = messageClient.receiveMessage(q1)

    // Then
    result must be (Some(m1))
  }

  test("receiving a message should retry if message is delivered by another thread") {
    // Given
    val (messageClient, mockStorage) = createMessageClientWithMockStorage
    val q1 = Queue("q1", VisibilityTimeout(123L))
    val m1 = Message(q1, "id1", "abc", MillisNextDelivery(NOW + 123L))
    val m2 = Message(q1, "id2", "xyz", MillisNextDelivery(NOW + 123L))

    when(mockStorage.lookupPendingMessage(musteq(q1), anyLong()))
            .thenReturn(Some(m1))
            .thenReturn(Some(m2))

    when(mockStorage.updateNextDelivery(any(classOf[SpecifiedMessage]), any(classOf[MillisNextDelivery])))
            .thenReturn(None) // m1 already delivered
            .thenReturn(Some(m2))

    // When
    val result = messageClient.receiveMessage(q1)

    // Then
    result must be (Some(m2))
  }

  def createMessageClientWithMockStorage: (MessageClient, MessageStorageModule#MessageStorage) = {
    val env = new NativeClientModule
      with MessageStorageModule
      with QueueStorageModule
      with NowModule {

      val mockMessageStorage = mock[MessageStorage]

      def messageStorage = mockMessageStorage
      def queueStorage = null

      override def now = NOW
    }

    (env.nativeMessageClientImpl, env.mockMessageStorage)
  }
}