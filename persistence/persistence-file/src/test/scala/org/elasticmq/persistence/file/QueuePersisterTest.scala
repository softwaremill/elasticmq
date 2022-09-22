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
        defaultVisibilityTimeout = MillisVisibilityTimeout.fromSeconds(10),
        delay = Duration.standardSeconds(8),
        receiveMessageWait = Duration.standardSeconds(3),
        created = new DateTime(),
        lastModified = new DateTime()
      )
    )
    val actual = QueuePersister.prepareQueuesConfig(queues)

    actual shouldBe
      """queues {
        | "queue1" {contentBasedDeduplication=false,copyTo="",defaultVisibilityTimeout=10000,delay=8000,fifo=false,moveTo="",receiveMessageWait=3000,tags{}}
        |}""".stripMargin
  }

  test("fifo queue is converted to config") {
    val queues = List(
      QueueData(
        name = "queue2.fifo",
        defaultVisibilityTimeout = MillisVisibilityTimeout.fromSeconds(10),
        delay = Duration.standardSeconds(8),
        receiveMessageWait = Duration.standardSeconds(3),
        created = new DateTime(),
        lastModified = new DateTime(),
        isFifo = true
      )
    )
    val actual = QueuePersister.prepareQueuesConfig(queues)

    actual shouldBe
      """queues {
        | "queue2.fifo" {contentBasedDeduplication=false,copyTo="",defaultVisibilityTimeout=10000,delay=8000,fifo=true,moveTo="",receiveMessageWait=3000,tags{}}
        |}""".stripMargin
  }

}
