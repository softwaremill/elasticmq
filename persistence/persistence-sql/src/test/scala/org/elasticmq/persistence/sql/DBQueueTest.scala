package org.elasticmq.persistence.sql

import org.elasticmq.persistence.CreateQueueMetadata
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class DBQueueTest extends AnyFunSuite with Matchers {

  test("queue data written by 1.x, without retention period and maximum message size, is read") {
    val data =
      """{"name":"q","defaultVisibilityTimeoutSeconds":30,"delaySeconds":0,"receiveMessageWaitSeconds":0,
        |"created":100,"lastModified":200,"isFifo":false,"hasContentBasedDeduplication":false,"tags":{}}""".stripMargin

    val queue = DBQueue("q", data.getBytes).toCreateQueue

    queue.name shouldBe "q"
    queue.defaultVisibilityTimeoutSeconds shouldBe Some(30L)
    queue.messageRetentionPeriodSeconds shouldBe None
    queue.maximumMessageSize shouldBe None
  }

  test("retention period and maximum message size are written and read back") {
    val queue = CreateQueueMetadata("q", messageRetentionPeriodSeconds = Some(120L), maximumMessageSize = Some(2048))

    DBQueue.from(queue).toCreateQueue shouldBe queue
  }
}
