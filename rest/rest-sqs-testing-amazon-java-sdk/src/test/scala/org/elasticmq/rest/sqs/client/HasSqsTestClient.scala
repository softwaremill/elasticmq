package org.elasticmq.rest.sqs.client

trait HasSqsTestClient {

  def testClient: SqsClient
}
