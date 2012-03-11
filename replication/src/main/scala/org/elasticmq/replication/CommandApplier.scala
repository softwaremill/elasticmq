package org.elasticmq.replication

import org.elasticmq.storage.IdempotentMutativeCommand

trait CommandApplier {
  def apply(command: IdempotentMutativeCommand[_])
}
