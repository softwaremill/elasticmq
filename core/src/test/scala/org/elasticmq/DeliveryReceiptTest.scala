package org.elasticmq

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class DeliveryReceiptTest extends AnyFunSuite with Matchers {
  test("should extract id") {
    // Given
    val id = MessageId("123-781-abc-def")

    // When
    val receipt = DeliveryReceipt.generate(id)

    // Then
    receipt.extractId should be(id)
  }
}
