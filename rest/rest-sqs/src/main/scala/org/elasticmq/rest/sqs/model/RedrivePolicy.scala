package org.elasticmq.rest.sqs.model

case class RedrivePolicy(queueName: String, region: Option[String], accountId: Option[String], maxReceiveCount: Int)
