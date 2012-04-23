package org.elasticmq.storage

import java.io.Closeable

package object filelog {
  def using[T](closeable: Closeable)(block: => T): T = {
    try {
      block
    } finally {
      closeable.close()
    }
  }

  type IdempotentMutativeCommandOrEnd = Either[IdempotentMutativeCommand[_], EndOfCommands.type]
}