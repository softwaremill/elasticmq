/*
 * All right reserved - Fronteer LTD
 */
package org.elasticmq.rest.sqs.model


case class RedrivePolicy(
  queueName: String,
  maxReceiveCount: Int
)

