package org.elasticmq.server.config

case class CreateQueue(name: String,
                       defaultVisibilityTimeoutSeconds: Option[Long],
                       delaySeconds: Option[Long],
                       receiveMessageWaitSeconds: Option[Long],
                       deadLettersQueue: Option[DeadLettersQueue],
                       isFifo: Boolean,
                       hasContentBasedDeduplication: Boolean,
                       copyMessagesTo: Option[String] = None,
                       moveMessagesTo: Option[String] = None,
                       tags: Map[String, String] = Map[String, String]())
