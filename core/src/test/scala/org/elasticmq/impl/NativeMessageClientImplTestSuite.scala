package org.elasticmq.impl

import org.scalatest.matchers.MustMatchers
import org.scalatest._

import mock.MockitoSugar
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.elasticmq._
import org.mockito.ArgumentMatcher
import org.joda.time.{Duration, DateTime}
import org.elasticmq.test.DataCreationHelpers
import org.elasticmq.storage.{StorageModule, MessageStatisticsStorageModule, MessageStorageModule}
import org.elasticmq.impl.nativeclient.{NativeQueueModule, NativeHelpersModule, NativeMessageModule, NativeClientModule}

class NativeMessageClientImplTestSuite extends FunSuite with MustMatchers with MockitoSugar with DataCreationHelpers {
  val Now = 1316168602L
  val NowAsDateTime = new DateTime(1316168602L)

  test("sending a message should generate an id, properly set the next delivery and the created date") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(123L))
    val (queueOperations, mockStorage, _) = createQueueOperationsWithMockStorage(q1)

    // When
    val msg = queueOperations.sendMessage("abc")

    // Then
    val expectedNextDelivery = Now
    verify(mockStorage).persistMessage(argThat(new ArgumentMatcher[MessageData]{
      def matches(msgRef: AnyRef) = msgRef.asInstanceOf[MessageData].nextDelivery.millis == expectedNextDelivery
    }))
    msg.nextDelivery must be (MillisNextDelivery(expectedNextDelivery))
    msg.created.getMillis must be (Now)
  }

  test("sending an immediate message to a delayed queue should properly set the next delivery") {
    // Given
    val delayedSeconds = 12
    val q1 = createQueueData("q1", MillisVisibilityTimeout(123L)).copy(delay = Duration.standardSeconds(delayedSeconds))
    val (queueOperations, mockStorage, _) = createQueueOperationsWithMockStorage(q1)

    // When
    val msg = queueOperations.sendMessage("abc")

    // Then
    val expectedNextDelivery = Now + delayedSeconds*1000
    verify(mockStorage).persistMessage(argThat(new ArgumentMatcher[MessageData]{
      def matches(msgRef: AnyRef) = msgRef.asInstanceOf[MessageData].nextDelivery.millis == expectedNextDelivery
    }))
    msg.nextDelivery must be (MillisNextDelivery(expectedNextDelivery))
  }

  test("should correctly bump a never received message statistics") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(123L))
    val (queueOperations, mockStorage, mockStatisticsStorage) = createQueueOperationsWithMockStorage(q1)

    val m = createMessageData("1", "z", MillisNextDelivery(123L))
    val stats = MessageStatistics(NeverReceived, 0)

    when(mockStorage.receiveMessage(anyLong(), any(classOf[MillisNextDelivery]))).thenReturn(Some(m))
    when(mockStatisticsStorage.readMessageStatistics(m.id)).thenReturn(stats)

    // When
    val messageStatsOption = queueOperations.receiveMessageWithStatistics(DefaultVisibilityTimeout).map(_._2)

    // Then
    val expectedMessageStats = MessageStatistics(OnDateTimeReceived(NowAsDateTime), 1)

    verify(mockStatisticsStorage).writeMessageStatistics(org.mockito.Matchers.eq(m.id),
      argThat(new ArgumentMatcher[MessageStatistics]{
        def matches(statsRef: AnyRef) = statsRef == expectedMessageStats
      }))

    messageStatsOption must be (Some(expectedMessageStats))
  }

  test("should correctly bump an already received message statistics") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(123L))

    val (queueOperations, mockStorage, mockStatisticsStorage) = createQueueOperationsWithMockStorage(q1)

    val m = createMessageData("1", "z", MillisNextDelivery(123L))
    val stats = MessageStatistics(OnDateTimeReceived(new DateTime(Now - 100000L)), 7)

    when(mockStorage.receiveMessage(anyLong(), any(classOf[MillisNextDelivery]))).thenReturn(Some(m))
    when(mockStatisticsStorage.readMessageStatistics(m.id)).thenReturn(stats)

    // When
    val messageStatsOption = queueOperations.receiveMessageWithStatistics(DefaultVisibilityTimeout).map(_._2)

    // Then
    val expectedMessageStats = MessageStatistics(stats.approximateFirstReceive, 8)

    verify(mockStatisticsStorage).writeMessageStatistics(org.mockito.Matchers.eq(m.id),
      argThat(new ArgumentMatcher[MessageStatistics]{
        def matches(statsRef: AnyRef) = statsRef == expectedMessageStats
      }))

    messageStatsOption must be (Some(expectedMessageStats))
  }

  test("should correctly set next delivery if receiving a message with default visibility timeout") {
    // Given
    val q = createQueueData("q1", MillisVisibilityTimeout(123L))

    val (queueOperations, mockStorage, mockStatisticsStorage) = createQueueOperationsWithMockStorage(q)

    val m = createMessageData("1", "z", MillisNextDelivery(123L))

    when(mockStatisticsStorage.readMessageStatistics(m.id)).thenReturn(
      MessageStatistics(OnDateTimeReceived(NowAsDateTime), 0))

    when(mockStorage.receiveMessage(anyLong(), any(classOf[MillisNextDelivery]))).thenReturn(Some(m))

    // When
    queueOperations.receiveMessage(DefaultVisibilityTimeout)

    // Then
    verify(mockStorage).receiveMessage(anyLong(), argThat(new ArgumentMatcher[MillisNextDelivery]{
      def matches(ndRef: AnyRef) = ndRef == MillisNextDelivery(Now + 123L)
    }))
  }

  test("should correctly set next delivery if receiving a message with a specific visibility timeout") {
    // Given
    val q = createQueueData("q1", MillisVisibilityTimeout(123L))

    val (queueOperations, mockStorage, mockStatisticsStorage) = createQueueOperationsWithMockStorage(q)

    val m = createMessageData("1", "z", MillisNextDelivery(123L))

    when(mockStatisticsStorage.readMessageStatistics(m.id)).thenReturn(
      MessageStatistics(OnDateTimeReceived(NowAsDateTime), 0))

    when(mockStorage.receiveMessage(anyLong(), any(classOf[MillisNextDelivery]))).thenReturn(Some(m))

    // When
    queueOperations.receiveMessage(MillisVisibilityTimeout(1000L))

    // Then
    verify(mockStorage).receiveMessage(anyLong(), argThat(new ArgumentMatcher[MillisNextDelivery]{
      def matches(ndRef: AnyRef) = ndRef == MillisNextDelivery(Now + 1000L)
    }))
  }

  def createQueueOperationsWithMockStorage(queueData: QueueData): (QueueOperations, MessageStorageModule#MessageStorage,
    MessageStatisticsStorageModule#MessageStatisticsStorage) = {
    val env = new NativeClientModule
      with NativeQueueModule
      with NativeHelpersModule
      with NativeMessageModule
      with StorageModule
      with NowModule
      with ImmediateVolatileTaskSchedulerModule {

      val mockQueueStorage = mock[QueueStorage]
      val mockMessageStorage = mock[MessageStorage]
      val mockMessageStatisticsStorage = mock[MessageStatisticsStorage]

      when(mockQueueStorage.lookupQueue(queueData.name)).thenReturn(Some(queueData))

      def queueStorage = mockQueueStorage
      def messageStorage(queueName: String) = mockMessageStorage
      def messageStatisticsStorage(queueName: String) = mockMessageStatisticsStorage

      override def nowAsDateTime = NowAsDateTime
    }

    (env.nativeClient.queueOperations(queueData.name), env.mockMessageStorage, env.mockMessageStatisticsStorage)
  }
}