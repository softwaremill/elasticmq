package org.elasticmq.rest.sqs

trait AwsConfiguration {

  def awsRegion: String

  def awsAccountId: String

  def getArn(queueName: String): String = s"arn:aws:sqs:$awsRegion:$awsAccountId:$queueName"
}
