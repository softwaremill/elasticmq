package org.elasticmq.server.config

case class CreateQueue(name: String,
                       defaultVisibilityTimeoutSeconds: Option[Long],
                       delaySeconds: Option[Long],
                       receiveMessageWaitSeconds: Option[Long],
                       deadLettersQueue: Option[DeadLettersQueue])

