package org.elasticmq.impl

import org.scalatest.matchers.MustMatchers
import org.scalatest._

import mock.MockitoSugar
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.elasticmq._
import org.elasticmq.storage.{MessageStatisticsStorageModule, MessageStorageModule, QueueStorageModule}
import org.mockito.{Matchers, ArgumentMatcher}
import org.joda.time.{Duration, DateTime}

class NativeMessageClientImplTestSuite extends FunSuite with MustMatchers with MockitoSugar {
  val Now = 1316168602L
  val NowAsDateTime = new DateTime(1316168602L)

  test("sending a message should generate an id, properly set the next delivery and the created date") {
    // Given
    val (messageClient, mockStorage, _) = createMessageClientWithMockStorage
    val q1 = Queue("q1", MillisVisibilityTimeout(123L))

    // When
    val msg = messageClient.sendMessage(Message(q1, "abc"))

    // Then
    val expectedNextDelivery = Now
    verify(mockStorage).persistMessage(argThat(new ArgumentMatcher[SpecifiedMessage]{
      def matches(msgRef: AnyRef) = msgRef.asInstanceOf[SpecifiedMessage].nextDelivery.millis == expectedNextDelivery
    }))
    msg.nextDelivery must be (MillisNextDelivery(expectedNextDelivery))
    msg.created.getMillis must be (Now)
  }

  test("sending an immediate message to a delayed queue should properly set the next delivery") {
    // Given
    val (messageClient, mockStorage, _) = createMessageClientWithMockStorage
    val delayedSeconds = 12
    val q1 = Queue("q1", MillisVisibilityTimeout(123L), Duration.standardSeconds(delayedSeconds))

    // When
    val msg = messageClient.sendMessage(Message(q1, "abc"))

    // Then
    val expectedNextDelivery = Now + delayedSeconds*1000
    verify(mockStorage).persistMessage(argThat(new ArgumentMatcher[SpecifiedMessage]{
      def matches(msgRef: AnyRef) = msgRef.asInstanceOf[SpecifiedMessage].nextDelivery.millis == expectedNextDelivery
    }))
    msg.nextDelivery must be (MillisNextDelivery(expectedNextDelivery))
  }

  test("should correctly bump a never received message statistics") {
    // Given
    val (messageClient, mockStorage, mockStatisticsStorage) = createMessageClientWithMockStorage

    val q1 = Queue("q1", MillisVisibilityTimeout(123L))
    val m = Message(q1, Some("1"), "z", MillisNextDelivery(123L))
    val stats = MessageStatistics(m, NeverReceived, 0)

    when(mockStorage.receiveMessage(Matchers.eq(q1), anyLong(), any(classOf[MillisNextDelivery])))
            .thenReturn(Some(m))
    when(mockStatisticsStorage.readMessageStatistics(m)).thenReturn(stats)

    // When
    val messageStatsOption = messageClient.receiveMessageWithStatistics(q1, DefaultVisibilityTimeout)

    // Then
    val expectedMessageStats = MessageStatistics(m, OnDateTimeReceived(NowAsDateTime), 1)

    verify(mockStatisticsStorage).writeMessageStatistics(argThat(new ArgumentMatcher[MessageStatistics]{
      def matches(statsRef: AnyRef) = statsRef == expectedMessageStats
    }))

    messageStatsOption must be (Some(expectedMessageStats))
  }

  test("should correctly bump an already received message statistics") {
    // Given
    val (messageClient, mockStorage, mockStatisticsStorage) = createMessageClientWithMockStorage

    val q1 = Queue("q1", MillisVisibilityTimeout(123L))
    val m = Message(q1, Some("1"), "z", MillisNextDelivery(123L))
    val stats = MessageStatistics(m, OnDateTimeReceived(new DateTime(Now - 100000L)), 7)

    when(mockStorage.receiveMessage(Matchers.eq(q1), anyLong(), any(classOf[MillisNextDelivery])))
            .thenReturn(Some(m))
    when(mockStatisticsStorage.readMessageStatistics(m)).thenReturn(stats)

    // When
    val messageStatsOption = messageClient.receiveMessageWithStatistics(q1, DefaultVisibilityTimeout)

    // Then
    val expectedMessageStats = MessageStatistics(m, stats.approximateFirstReceive, 8)

    verify(mockStatisticsStorage).writeMessageStatistics(argThat(new ArgumentMatcher[MessageStatistics]{
      def matches(statsRef: AnyRef) = statsRef == expectedMessageStats
    }))

    messageStatsOption must be (Some(expectedMessageStats))
  }

  test("should correctly set next delivery if receiving a message with default visibility timeout") {
    // Given
    val (messageClient, mockStorage, mockStatisticsStorage) = createMessageClientWithMockStorage

    val q = Queue("q1", MillisVisibilityTimeout(123L))
    val m = Message(q, Some("1"), "z", MillisNextDelivery(123L))

    when(mockStatisticsStorage.readMessageStatistics(m)).thenReturn(MessageStatistics(m, OnDateTimeReceived(NowAsDateTime), 0))

    when(mockStorage.receiveMessage(any(classOf[Queue]), anyLong(), any(classOf[MillisNextDelivery]))).thenReturn(Some(m))

    // When
    messageClient.receiveMessage(q, DefaultVisibilityTimeout)

    // Then
    verify(mockStorage).receiveMessage(any(classOf[Queue]), anyLong(), argThat(new ArgumentMatcher[MillisNextDelivery]{
      def matches(ndRef: AnyRef) = ndRef == MillisNextDelivery(Now + 123L)
    }))
  }

  test("should correctly set next delivery if receiving a message with a specific visibility timeout") {
    // Given
    val (messageClient, mockStorage, mockStatisticsStorage) = createMessageClientWithMockStorage

    val q = Queue("q1", MillisVisibilityTimeout(123L))
    val m = Message(q, Some("1"), "z", MillisNextDelivery(123L))

    when(mockStatisticsStorage.readMessageStatistics(m)).thenReturn(MessageStatistics(m, OnDateTimeReceived(NowAsDateTime), 0))

    when(mockStorage.receiveMessage(any(classOf[Queue]), anyLong(), any(classOf[MillisNextDelivery]))).thenReturn(Some(m))

    // When
    messageClient.receiveMessage(q, MillisVisibilityTimeout(1000L))

    // Then
    verify(mockStorage).receiveMessage(any(classOf[Queue]), anyLong(), argThat(new ArgumentMatcher[MillisNextDelivery]{
      def matches(ndRef: AnyRef) = ndRef == MillisNextDelivery(Now + 1000L)
    }))
  }

  def createMessageClientWithMockStorage: (MessageClient, MessageStorageModule#MessageStorage,
          MessageStatisticsStorageModule#MessageStatisticsStorage) = {
    val env = new NativeClientModule
      with MessageStorageModule
      with QueueStorageModule
      with MessageStatisticsStorageModule
      with NowModule
      with ImmediateVolatileTaskSchedulerModule {

      val mockMessageStorage = mock[MessageStorage]
      val mockMessageStatisticsStorage = mock[MessageStatisticsStorage]

      def messageStorage = mockMessageStorage
      def queueStorage = null
      def messageStatisticsStorage = mockMessageStatisticsStorage

      override def nowAsDateTime = NowAsDateTime
    }

    (env.nativeMessageClientImpl, env.mockMessageStorage, env.mockMessageStatisticsStorage)
  }
}