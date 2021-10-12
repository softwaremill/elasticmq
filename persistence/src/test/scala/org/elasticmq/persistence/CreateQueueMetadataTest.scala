package org.elasticmq.persistence
import org.joda.time.DateTime
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CreateQueueMetadataTest extends AnyFunSuite with Matchers with OptionValues {

  test("CreateQueueMetadata and CreateQueue structures should be exchangeable") {
    val createQueue = CreateQueueMetadata(
      "xyz", Some(5), Some(10), Some(15), Some(DeadLettersQueue("dlq", 3)),
      isFifo = true,
      hasContentBasedDeduplication = true,
      copyMessagesTo = Some("xyz_copy"),
      moveMessagesTo = Some("xyz_move"),
      tags = Map("abc" -> "123")
    )

    CreateQueueMetadata.from(createQueue.toQueueData(new DateTime())) shouldBe createQueue
  }
}
