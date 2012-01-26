package org.elasticmq.impl

import org.scalatest.matchers.MustMatchers
import org.scalatest._

import mock.MockitoSugar
import org.mockito.Mockito.verify
import org.elasticmq._
import org.joda.time.DateTime
import org.mockito.ArgumentMatcher
import org.mockito.Matchers._
import org.elasticmq.impl.nativeclient.{NativeMessageModule, NativeHelpersModule, NativeQueueModule, NativeClientModule}
import org.elasticmq.storage.{StorageModule, QueueStorageModule}

class NativeClientImplTestSuite extends FunSuite with MustMatchers with MockitoSugar {
  val NOW = new DateTime(1316168602L)

  test("creating a queue should properly set the created and modified dates") {
    // Given
    val (client, mockStorage) = createClient

    // When
    val q1created = client.createQueue("q1")

    // Then
    def checkQueue(qd: QueueData) {
      qd.created must be (NOW)
      qd.lastModified must be (NOW)
    }

    verify(mockStorage).persistQueue(argThat(new ArgumentMatcher[QueueData]{
      def matches(arg: AnyRef) = { checkQueue(arg.asInstanceOf[QueueData]); true }
    }))

    q1created.created must be (NOW)
    q1created.lastModified must be (NOW)
  }

  def createClient: (Client, QueueStorageModule#QueueStorage) = {
    val env = new NativeClientModule
      with NativeQueueModule
      with NativeHelpersModule
      with NativeMessageModule
      with StorageModule
      with NowModule
      with ImmediateVolatileTaskSchedulerModule {

      val mockQueueStorage = mock[QueueStorage]

      def messageStorage(queueName: String) = null
      def messageStatisticsStorage(queueName: String) = null

      def queueStorage = mockQueueStorage

      override def nowAsDateTime = NOW
    }

    (env.nativeClient, env.mockQueueStorage)
  }
}