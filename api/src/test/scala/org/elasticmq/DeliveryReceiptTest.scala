package org.elasticmq

import org.scalatest.matchers.MustMatchers
import org.scalatest.FunSuite

class DeliveryReceiptTest extends FunSuite with MustMatchers {
  test("should extract id") {
    // Given
    val id = MessageId("123-781-abc-def")

    // When
    val receipt = DeliveryReceipt.generate(id)

    // Then
    receipt.extractId must be (id)
  }
}
