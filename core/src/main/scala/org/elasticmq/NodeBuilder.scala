package org.elasticmq

import org.elasticmq.storage.{StorageCommandExecutor, StorageModule}
import org.elasticmq.impl.scheduler.BackgroundVolatileTaskSchedulerModule
import org.elasticmq.impl.{NowModule, NodeImpl}
import org.elasticmq.impl.nativeclient.NativeModule

object NodeBuilder {
  def withStorage(storage: StorageCommandExecutor) = {
    val env = new NativeModule
      with NowModule
      with BackgroundVolatileTaskSchedulerModule
      with StorageModule {
      def storageCommandExecutor = storage
    }

    new NodeImpl(env.nativeClient, () => storage.shutdown())
  }
}

