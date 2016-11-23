package org.elasticmq

import org.scalatest.Matchers
import org.scalatest.FunSuite

class DeliveryReceiptTest extends FunSuite with Matchers {
  test("should extract id") {
    // Given
    val id = MessageId("123-781-abc-def")

    // When
    val receipt = DeliveryReceipt.generate(id)

    // Then
    receipt.extractId should be (id)
  }
}
