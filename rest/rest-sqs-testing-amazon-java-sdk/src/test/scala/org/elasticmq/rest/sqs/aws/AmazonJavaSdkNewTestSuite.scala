package org.elasticmq.rest.sqs.aws

import org.elasticmq.rest.sqs.{SqsClientServerCommunication, SqsClientServerWithSdkV2Communication}

abstract class AmazonJavaSdkNewTestSuite
    extends AmazonJavaSdkNewTestBase
    with QueueOperationsTests
    with QueueAttributesTests
    with MessageOperationsTests
    with FifoQueueTests
    with DeadLetterQueueTests

class AmazonJavaSdkV1TestSuite extends AmazonJavaSdkNewTestSuite with SqsClientServerCommunication
class AmazonJavaSdkV2TestSuite extends AmazonJavaSdkNewTestSuite with SqsClientServerWithSdkV2Communication
