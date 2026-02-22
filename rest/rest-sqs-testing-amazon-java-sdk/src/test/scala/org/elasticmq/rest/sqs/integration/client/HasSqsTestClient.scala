package org.elasticmq.rest.sqs.integration.client

trait HasSqsTestClient {

  def testClient: SqsClient
}
