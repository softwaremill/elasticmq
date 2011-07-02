package org.elasticmq.impl

import org.scalatest.matchers.MustMatchers
import org.scalatest._

import mock.MockitoSugar
import org.mockito.Mockito.{when, verify}
import org.mockito.Matchers._
import org.mockito.Matchers.{eq => musteq}
import org.elasticmq.storage.{MessageStorage, Storage}
import org.elasticmq.{Queue, Message}

class NativeMessageClientImplTestSuite extends FunSuite with MustMatchers with MockitoSugar {
  test("sending a message should generate an id and properly set the visibility timeout") {
    // Given
    val mockStorage = createMockStorage
    val q1 = Queue("q1", 123L)

    // When
    new NativeMessageClientImpl(mockStorage).sendMessage(Message(q1, "abc"))

    // Then
    verify(mockStorage.messageStorage).persistMessage(Message(q1, anyString(), "abc", 123L, 0L))
  }

  test("receiving a message should return None if there are no pending messages") {
    // Given
    val mockStorage = createMockStorage
    val q1 = Queue("q1", 123L)

    when(mockStorage.messageStorage.lookupPendingMessage(musteq(q1), anyLong())).thenReturn(None)

    // When
    val result = new NativeMessageClientImpl(mockStorage).receiveMessage(q1)

    // Then
    result must be (None)
  }

  test("receiving a message should return a message if there is a pending message") {
    // Given
    val mockStorage = createMockStorage
    val q1 = Queue("q1", 123L)
    val m1 = Message(q1, "id1", "abc", 10L, 50L)

    when(mockStorage.messageStorage.lookupPendingMessage(musteq(q1), anyLong())).thenReturn(Some(m1))
    when(mockStorage.messageStorage.updateLastDelivered(musteq(m1), anyLong())).thenReturn(Some(m1))

    // When
    val result = new NativeMessageClientImpl(mockStorage).receiveMessage(q1)

    // Then
    result must be (Some(m1))
  }

  test("receiving a message should retry if message is delivered by another thread") {
    // Given
    val mockStorage = createMockStorage
    val q1 = Queue("q1", 123L)
    val m1 = Message(q1, "id1", "abc", 10L, 50L)
    val m2 = Message(q1, "id2", "xyz", 11L, 51L)

    when(mockStorage.messageStorage.lookupPendingMessage(musteq(q1), anyLong()))
            .thenReturn(Some(m1))
            .thenReturn(Some(m2))

    when(mockStorage.messageStorage.updateLastDelivered(any(classOf[Message]), anyLong()))
            .thenReturn(None) // m1 already delivered
            .thenReturn(Some(m2))

    // When
    val result = new NativeMessageClientImpl(mockStorage).receiveMessage(q1)

    // Then
    result must be (Some(m2))
  }

  def createMockStorage = {
    val mockMessageStorage = mock[MessageStorage]
    val mockStorage = mock[Storage]
    when(mockStorage.messageStorage).thenReturn(mockMessageStorage)
    mockStorage
  }
}