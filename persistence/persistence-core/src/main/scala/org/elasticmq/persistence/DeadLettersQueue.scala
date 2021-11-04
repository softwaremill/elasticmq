package org.elasticmq.persistence

case class DeadLettersQueue(name: String, maxReceiveCount: Int)
