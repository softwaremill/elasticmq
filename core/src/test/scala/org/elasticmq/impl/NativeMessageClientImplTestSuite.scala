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
import org.elasticmq.impl.nativeclient.NativeModule
import org.elasticmq.data.QueueData
import org.elasticmq.storage._

class NativeMessageClientImplTestSuite extends FunSuite with MustMatchers with MockitoSugar with DataCreationHelpers {
  val Now = 1316168602L
  val NowAsDateTime = new DateTime(1316168602L)

  test("sending a message should generate an id, properly set the next delivery and the created date") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(123L))
    val (queueOperations, mockExecutor) = createQueueOperationsWithMockStorage(q1)

    // When
    val msg = queueOperations.sendMessage("abc")

    // Then
    val expectedNextDelivery = Now
    verify(mockExecutor).execute(argThat(new ArgumentMatcher[SendMessageCommand]{
      def matches(msgRef: AnyRef) = msgRef.asInstanceOf[SendMessageCommand].message.nextDelivery.millis == expectedNextDelivery
    }))
    msg.nextDelivery must be (MillisNextDelivery(expectedNextDelivery))
    msg.created.getMillis must be (Now)
  }

  test("sending an immediate message to a delayed queue should properly set the next delivery") {
    // Given
    val delayedSeconds = 12
    val q1 = createQueueData("q1", MillisVisibilityTimeout(123L)).copy(delay = Duration.standardSeconds(delayedSeconds))
    val (queueOperations, mockExecutor) = createQueueOperationsWithMockStorage(q1)

    // When
    val msg = queueOperations.sendMessage("abc")

    // Then
    val expectedNextDelivery = Now + delayedSeconds*1000
    verify(mockExecutor).execute(argThat(new ArgumentMatcher[SendMessageCommand]{
      def matches(cmdRef: AnyRef) = cmdRef.asInstanceOf[SendMessageCommand].message.nextDelivery.millis == expectedNextDelivery
    }))
    msg.nextDelivery must be (MillisNextDelivery(expectedNextDelivery))
  }

  test("should correctly bump a never received message statistics") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(123L))
    val (queueOperations, mockExecutor) = createQueueOperationsWithMockStorage(q1)

    val m = createMessageData("1", "z", MillisNextDelivery(123L))
    val stats = MessageStatistics(NeverReceived, 0)

    when(mockExecutor.execute(ReceiveMessageCommand(anyString(), anyLong(), any(classOf[MillisNextDelivery])))).thenReturn(Some(m))
    when(mockExecutor.execute(GetMessageStatisticsCommand("q1", m.id))).thenReturn(stats)

    // When
    val messageStatsOption = queueOperations.receiveMessageWithStatistics(DefaultVisibilityTimeout).map(_._2)

    // Then
    val expectedMessageStats = MessageStatistics(OnDateTimeReceived(NowAsDateTime), 1)

    verify(mockExecutor).execute(argThat(new ArgumentMatcher[UpdateMessageStatisticsCommand]{
      def matches(cmdRef: AnyRef) = {
        val cmd = cmdRef.asInstanceOf[UpdateMessageStatisticsCommand]
        cmd.messageStatistics == expectedMessageStats
      }
    }))

    messageStatsOption must be (Some(expectedMessageStats))
  }

  test("should correctly bump an already received message statistics") {
    // Given
    val q1 = createQueueData("q1", MillisVisibilityTimeout(123L))

    val (queueOperations, mockExecutor) = createQueueOperationsWithMockStorage(q1)

    val m = createMessageData("1", "z", MillisNextDelivery(123L))
    val stats = MessageStatistics(OnDateTimeReceived(new DateTime(Now - 100000L)), 7)

    when(mockExecutor.execute(ReceiveMessageCommand(anyString(), anyLong(), any(classOf[MillisNextDelivery])))).thenReturn(Some(m))
    when(mockExecutor.execute(GetMessageStatisticsCommand("q1", m.id))).thenReturn(stats)

    // When
    val messageStatsOption = queueOperations.receiveMessageWithStatistics(DefaultVisibilityTimeout).map(_._2)

    // Then
    val expectedMessageStats = MessageStatistics(stats.approximateFirstReceive, 8)

    verify(mockExecutor).execute(argThat(new ArgumentMatcher[UpdateMessageStatisticsCommand]{
      def matches(cmdRef: AnyRef) = {
        val cmd = cmdRef.asInstanceOf[UpdateMessageStatisticsCommand]
        cmd.messageStatistics == expectedMessageStats
      }
    }))

    messageStatsOption must be (Some(expectedMessageStats))
  }

  test("should correctly set next delivery if receiving a message with default visibility timeout") {
    // Given
    val q = createQueueData("q1", MillisVisibilityTimeout(123L))

    val (queueOperations, mockExecutor) = createQueueOperationsWithMockStorage(q)

    val m = createMessageData("1", "z", MillisNextDelivery(123L))

    when(mockExecutor.execute(GetMessageStatisticsCommand("q1", m.id))).thenReturn(MessageStatistics(OnDateTimeReceived(NowAsDateTime), 0))    
    when(mockExecutor.execute(ReceiveMessageCommand(anyString(), anyLong(), any(classOf[MillisNextDelivery])))).thenReturn(Some(m))

    // When
    queueOperations.receiveMessage(DefaultVisibilityTimeout)

    // Then
    verify(mockExecutor).execute(argThat(new ArgumentMatcher[ReceiveMessageCommand]{
      def matches(cmdRef: AnyRef) = cmdRef.asInstanceOf[ReceiveMessageCommand].newNextDelivery == MillisNextDelivery(Now + 123L)
    }))
  }

  test("should correctly set next delivery if receiving a message with a specific visibility timeout") {
    // Given
    val q = createQueueData("q1", MillisVisibilityTimeout(123L))

    val (queueOperations, mockExecutor) = createQueueOperationsWithMockStorage(q)

    val m = createMessageData("1", "z", MillisNextDelivery(123L))

    when(mockExecutor.execute(GetMessageStatisticsCommand("q1", m.id))).thenReturn(MessageStatistics(OnDateTimeReceived(NowAsDateTime), 0))
    when(mockExecutor.execute(ReceiveMessageCommand(anyString(), anyLong(), any(classOf[MillisNextDelivery])))).thenReturn(Some(m))

    // When
    queueOperations.receiveMessage(MillisVisibilityTimeout(1000L))

    // Then
    verify(mockExecutor).execute(argThat(new ArgumentMatcher[ReceiveMessageCommand]{
      def matches(cmdRef: AnyRef) = cmdRef.asInstanceOf[ReceiveMessageCommand].newNextDelivery == MillisNextDelivery(Now + 1000L)
    }))
  }

  def createQueueOperationsWithMockStorage(queueData: QueueData): (QueueOperations, StorageCommandExecutor) = {
    val env = new NativeModule
      with StorageModule
      with NowModule
      with ImmediateVolatileTaskSchedulerModule {

      val mockStorageCommandExecutor = mock[StorageCommandExecutor]

      when(mockStorageCommandExecutor.execute(LookupQueueCommand(queueData.name))).thenReturn(Some(queueData))

      def storageCommandExecutor = mockStorageCommandExecutor

      override def nowAsDateTime = NowAsDateTime
    }

    (env.nativeClient.queueOperations(queueData.name), env.mockStorageCommandExecutor)
  }
}