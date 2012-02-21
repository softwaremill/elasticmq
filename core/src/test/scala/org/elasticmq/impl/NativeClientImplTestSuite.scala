package org.elasticmq.impl

import org.scalatest.matchers.MustMatchers
import org.scalatest._

import mock.MockitoSugar
import org.mockito.Mockito.verify
import org.elasticmq._
import org.joda.time.DateTime
import org.mockito.ArgumentMatcher
import org.mockito.Matchers._
import org.elasticmq.impl.nativeclient.NativeModule
import org.elasticmq.data.QueueData
import org.elasticmq.storage.{CreateQueueCommand, StorageCommandExecutor, StorageModule}

class NativeClientImplTestSuite extends FunSuite with MustMatchers with MockitoSugar {
  val NOW = new DateTime(1316168602L)

  test("creating a queue should properly set the created and modified dates") {
    // Given
    val (client, storageCommandExecutor) = createClient

    // When
    val q1created = client.createQueue("q1")

    // Then
    def checkQueue(qd: QueueData) {
      qd.created must be (NOW)
      qd.lastModified must be (NOW)
    }

    verify(storageCommandExecutor).execute(argThat(new ArgumentMatcher[CreateQueueCommand]{
      def matches(arg: AnyRef) = { checkQueue(arg.asInstanceOf[CreateQueueCommand].queue); true }
    }))

    q1created.created must be (NOW)
    q1created.lastModified must be (NOW)
  }

  def createClient: (Client, StorageCommandExecutor) = {
    val env = new NativeModule
      with StorageModule
      with NowModule
      with ImmediateVolatileTaskSchedulerModule {

      val storageCommandExecutor = mock[StorageCommandExecutor]

      override def nowAsDateTime = NOW
    }

    (env.nativeClient, env.storageCommandExecutor)
  }
}