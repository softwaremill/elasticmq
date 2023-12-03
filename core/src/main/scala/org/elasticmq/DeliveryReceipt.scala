package org.elasticmq

import java.util.UUID

sealed case class DeliveryReceipt(receipt: String) {
  def extractId: MessageId =
    MessageId(receipt.split(DeliveryReceipt.Separator)(0))

  override def toString = receipt
}

object DeliveryReceipt {
  private val Separator = "#"

  def generate(id: MessageId) =
    new DeliveryReceipt(String.valueOf(id) + Separator + UUID.randomUUID().toString)
}
