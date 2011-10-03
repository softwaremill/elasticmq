package org.elasticmq.impl

import org.scalatest.FunSuite
import org.scalatest.matchers.MustMatchers
import java.util.concurrent.atomic.{AtomicLong, AtomicInteger}

class BackgroundTaskSchedulerTestSuite extends FunSuite with MustMatchers {
  test("should run specified block") {
     // Given
    val box = new AtomicInteger(0)

    val thisThreadId = Thread.currentThread().getId
    val threadIdBox = new AtomicLong(thisThreadId)

    val scheduler = new BackgroundTaskSchedulerModule {}

    // When
    scheduler.backgroundTaskScheduler.schedule { box.set(10); threadIdBox.set(Thread.currentThread().getId) }

    // Then
    Thread.sleep(250)
    box.get() must be (10)
    threadIdBox.get() must not be (thisThreadId)
  }
}