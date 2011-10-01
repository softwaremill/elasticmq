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
    val expectedNextDelivery = NOW
    verify(mockStorage).persistMessage(argThat(new ArgumentMatcher[SpecifiedMessage]{
      def matches(msgRef: AnyRef) = msgRef.asInstanceOf[SpecifiedMessage].nextDelivery.millis == expectedNextDelivery
    }))
    msg.nextDelivery must be (MillisNextDelivery(expectedNextDelivery))
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