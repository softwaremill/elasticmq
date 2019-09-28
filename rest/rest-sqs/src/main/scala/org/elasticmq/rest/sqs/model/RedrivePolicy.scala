package org.elasticmq.rest.sqs.model

case class GenericRedrivePolicy[T[_]](queueName: String, region: T[String], accountId: T[String], maxReceiveCount: Int)

object RedrivePolicy {
  type BackwardCompatibleRedrivePolicy = GenericRedrivePolicy[Option]
  type RedrivePolicy = GenericRedrivePolicy[Identity]
  type Identity[A] = A

  def apply(queueName: String, region: String, accountId: String, maxReceiveCount: Int): RedrivePolicy =
    new RedrivePolicy(queueName, region, accountId, maxReceiveCount)
}
