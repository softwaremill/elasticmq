package org.elasticmq.rest.sqs.integration

import org.elasticmq.rest.sqs.integration.common.{IntegrationTestsBase, SQSRestServerWithSdkV2Client}

class CustomServerContextTests extends IntegrationTestsBase with SQSRestServerWithSdkV2Client {

  override val awsRegion = "elasticmq"
  override val awsAccountId = "123456789012"
  override val serverPort = 9324
  override val serverContextPath = "xyz"

  test("should create queue when context path is set") {
    val queueUrl = testClient.createQueue("testQueue").toOption.get
    queueUrl should be("http://localhost:9324/xyz/123456789012/testQueue")
  }

  test("should send and receive messages when context path is set") {
    val queueUrl = testClient.createQueue("testQueue").toOption.get

    testClient.sendMessage("http://localhost:9324/xyz/123456789012/testQueue", "test msg1").isRight shouldBe true
    testClient.sendMessage("http://localhost:9324/xyz/queue/testQueue", "test msg2").isRight shouldBe true
    testClient.sendMessage("http://localhost:9324/xyz/abc/testQueue", "test msg3").isRight shouldBe true
    testClient.sendMessage("http://localhost:9324/xyz/testQueue", "test msg4").isRight shouldBe true

    val messages = testClient.receiveMessage(queueUrl, maxNumberOfMessages = Some(10))

    messages.map(_.body) should contain allOf ("test msg1", "test msg2", "test msg3", "test msg4")
  }
}
