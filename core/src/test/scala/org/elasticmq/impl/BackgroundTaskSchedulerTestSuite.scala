package org.elasticmq.impl

import org.scalatest.FunSuite
import org.scalatest.matchers.MustMatchers
import java.util.concurrent.atomic.AtomicInteger

class BackgroundTaskSchedulerTestSuite extends FunSuite with MustMatchers {
  test("should run specified block") {
     // Given
    val box = new AtomicInteger(0)
    val scheduler = new BackgroundTaskSchedulerModule {}

    // When
    scheduler.backgroundTaskScheduler.schedule(() => { box.set(10)})

    // Then
    Thread.sleep(250)
    box.get() must be (10)
  }
}