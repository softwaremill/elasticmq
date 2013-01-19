package org.elasticmq

import org.elasticmq.storage.{StorageCommandExecutor, StorageModule}
import org.elasticmq.impl.scheduler.BackgroundVolatileTaskSchedulerModule
import org.elasticmq.impl.{NowModule, NodeImpl}
import org.elasticmq.impl.nativeclient.NativeModule
import com.typesafe.scalalogging.slf4j.Logging

object NodeBuilder extends Logging {
  def withStorage(storage: StorageCommandExecutor): Node = {
    val env = new NativeModule
      with NowModule
      with BackgroundVolatileTaskSchedulerModule
      with StorageModule {
      def storageCommandExecutor = storage
    }

    val node = new NodeImpl(env.nativeClient, () => storage.shutdown())

    logger.info("Started new node with storage %s".format(storage.getClass.getSimpleName))

    node
  }
}

