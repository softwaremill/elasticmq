package org.elasticmq.persistence
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CreateQueueMetadataTest extends AnyFunSuite with Matchers with OptionValues {

  test("CreateQueueMetadata and CreateQueue structures should be exchangeable") {
    val createQueue = CreateQueueMetadata(
      name = "xyz",
      defaultVisibilityTimeoutSeconds = Some(5),
      delaySeconds = Some(10),
      receiveMessageWaitSeconds = Some(15),
      created = 10,
      lastModified = 15,
      deadLettersQueue = Some(DeadLettersQueue("dlq", 3)),
      isFifo = true,
      hasContentBasedDeduplication = true,
      copyMessagesTo = Some("xyz_copy"),
      moveMessagesTo = Some("xyz_move"),
      tags = Map("abc" -> "123")
    )

    CreateQueueMetadata.from(createQueue.toCreateQueueRequest.toQueueData) shouldBe createQueue
  }
}
