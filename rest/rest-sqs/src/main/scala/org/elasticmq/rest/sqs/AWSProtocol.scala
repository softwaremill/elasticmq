package org.elasticmq.rest.sqs

sealed trait AWSProtocol extends Product with Serializable

object AWSProtocol {
  case object AWSQueryProtocol extends AWSProtocol
  case object `AWSJsonProtocol1.0` extends AWSProtocol
}
