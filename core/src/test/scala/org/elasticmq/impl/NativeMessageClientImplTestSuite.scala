package org.elasticmq.impl

import org.scalatest.matchers.MustMatchers
import org.scalatest._

import mock.MockitoSugar
import org.mockito.Mockito._
import org.mockito.Matchers
import org.mockito.Matchers._
import org.elasticmq._
import org.joda.time.DateTime
import org.elasticmq.storage.{MessageStatisticsStorageModule, MessageStorageModule, QueueStorageModule}
import org.mockito.{Matchers, ArgumentMatcher}

class NativeMessageClientImplTestSuite extends FunSuite with MustMatchers with MockitoSugar {
  val NOW = 1316168602L

  test("sending a message should generate an id, properly set the next delivery and the created date") {
    // Given
    val (messageClient, mockStorage, _) = createMessageClientWithMockStorage
    val q1 = Queue("q1", VisibilityTimeout(123L))

    // When
    val msg = messageClient.sendMessage(Message(q1, "abc"))

    // Then
    val expectedNextDelivery = NOW
    verify(mockStorage).persistMessage(argThat(new ArgumentMatcher[SpecifiedMessage]{
      def matches(msgRef: AnyRef) = msgRef.asInstanceOf[SpecifiedMessage].nextDelivery.millis == expectedNextDelivery
    }))
    msg.nextDelivery must be (MillisNextDelivery(expectedNextDelivery))
    msg.created.getMillis must be (NOW)
  }

  test("receiving a message should notify statistics") {
    // Given
    val (messageClient, mockStorage, mockStatisticsStorage) = createMessageClientWithMockStorage
    val q1 = Queue("q1", VisibilityTimeout(123L))
    val m = Message(q1, Some("1"), "z", MillisNextDelivery(123L))
    when(mockStorage.receiveMessage(Matchers.eq(q1), anyLong(), any(classOf[MillisNextDelivery])))
            .thenReturn(Some(m))

    // When
    val result = messageClient.receiveMessage(q1)

    // Then
    verify(mockStatisticsStorage).messageReceived(argThat(new ArgumentMatcher[IdentifiableMessage]{
      def matches(msgRef: AnyRef) = msgRef.asInstanceOf[IdentifiableMessage] == m
    }), Matchers.eq(NOW))
    result must be (Some(m))
  }

  def createMessageClientWithMockStorage: (MessageClient, MessageStorageModule#MessageStorage,
          MessageStatisticsStorageModule#MessageStatisticsStorage) = {
    val env = new NativeClientModule
      with MessageStorageModule
      with QueueStorageModule
      with MessageStatisticsStorageModule
      with NowModule {

      val mockMessageStorage = mock[MessageStorage]
      val mockMessageStatisticsStorage = mock[MessageStatisticsStorage]

      def messageStorage = mockMessageStorage
      def queueStorage = null
      def messageStatisticsStorage = mockMessageStatisticsStorage

      override def nowAsDateTime = new DateTime(NOW)
    }

    (env.nativeMessageClientImpl, env.mockMessageStorage, env.mockMessageStatisticsStorage)
  }
}