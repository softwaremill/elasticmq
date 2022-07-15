package org.elasticmq.persistence.file
import org.elasticmq.{MillisVisibilityTimeout, QueueData}
import org.joda.time.{DateTime, Duration}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class QueuePersisterTest extends AnyFunSuite with Matchers {
  test("standard queue is converted to config") {

    val queues = List(
      QueueData(
        name = "queue1",
        defaultVisibilityTimeout = MillisVisibilityTimeout(10),
        delay = Duration.ZERO,
        receiveMessageWait = Duration.standardSeconds(10),
        created = new DateTime(),
        lastModified = new DateTime()
      )
    )
    val actual = QueuePersister.prepareQueuesConfig(queues)

    actual shouldBe
      """queues {
        | "queue1" {contentBasedDeduplication=false,copyTo="",defaultVisibilityTimeout=10,delay=0,fifo=false,moveTo="",receiveMessageWait=10,tags{}}
        |}""".stripMargin
  }

  test("fifo queue is converted to config") {
    val queues = List(
      QueueData(
        name = "queue2.fifo",
        defaultVisibilityTimeout = MillisVisibilityTimeout(10),
        delay = Duration.ZERO,
        receiveMessageWait = Duration.standardSeconds(10),
        created = new DateTime(),
        lastModified = new DateTime(),
        isFifo = true
      )
    )
    val actual = QueuePersister.prepareQueuesConfig(queues)

    actual shouldBe
      """queues {
        | "queue2.fifo" {contentBasedDeduplication=false,copyTo="",defaultVisibilityTimeout=10,delay=0,fifo=true,moveTo="",receiveMessageWait=10,tags{}}
        |}""".stripMargin
  }

}
