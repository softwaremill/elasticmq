package org.elasticmq.impl

import org.scalatest.matchers.MustMatchers
import org.scalatest._

import mock.MockitoSugar
import org.mockito.Mockito.verify
import org.elasticmq._
import org.joda.time.DateTime
import org.mockito.ArgumentMatcher
import org.mockito.Matchers._
import org.elasticmq.storage.{MessageStatisticsStorageModule, MessageStorageModule, QueueStorageModule}

class NativeQueueClientImplTestSuite extends FunSuite with MustMatchers with MockitoSugar {
  val NOW = new DateTime(1316168602L)

  test("creating a queue should properly set the created and modified dates") {
    // Given
    val (queueClient, mockStorage) = createQueueClient
    val q1 = Queue("q1", MillisVisibilityTimeout(123L))

    // When
    val q1created = queueClient.createQueue(q1)

    // Then
    def checkQueue(q: Queue) {
      q.created must be (NOW)
      q.lastModified must be (NOW)
    }

    verify(mockStorage).persistQueue(argThat(new ArgumentMatcher[Queue]{
      def matches(arg: AnyRef) = { checkQueue(arg.asInstanceOf[Queue]); true }
    }))
    checkQueue(q1created)
  }

  def createQueueClient: (QueueClient, QueueStorageModule#QueueStorage) = {
    val env = new NativeClientModule
      with MessageStorageModule
      with QueueStorageModule
      with MessageStatisticsStorageModule
      with NowModule
      with ImmediateVolatileTaskSchedulerModule {

      val mockQueueStorage = mock[QueueStorage]

      def messageStorage = null
      def queueStorage = mockQueueStorage
      def messageStatisticsStorage = null

      override def nowAsDateTime = NOW
    }

    (env.nativeQueueClientImpl, env.mockQueueStorage)
  }
}