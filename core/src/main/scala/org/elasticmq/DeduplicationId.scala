package org.elasticmq

import java.security.MessageDigest

case class DeduplicationId(id: String) extends AnyVal

object DeduplicationId {
  def fromMessageBody(body: String): DeduplicationId = DeduplicationId(sha256Hash(body))

  private def sha256Hash(text: String): String = {
    String.format(
      "%064x",
      new java.math.BigInteger(1, MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8")))
    )
  }
}
