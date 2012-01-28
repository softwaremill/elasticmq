package org.elasticmq.impl.nativeclient

import org.elasticmq.impl.NowModule
import org.elasticmq.impl.scheduler.VolatileTaskSchedulerModule
import org.elasticmq.storage.StorageModule

trait NativeModule extends NativeClientModule with NativeQueueModule with NativeMessageModule with NativeHelpersModule {
  this: NowModule with VolatileTaskSchedulerModule with StorageModule =>
}