package org.elasticmq.rest.sqs.integration.multisdk

abstract class AmazonJavaMultiSdkTestSuite
    extends AmazonJavaMultiSdkTestBase
    with QueueOperationsTests
    with QueueAttributesTests
    with MessageOperationsTests
    with FifoQueueTests
    with DeadLetterQueueTests
    with MessageMoveTaskTests
    with CreateQueueRaceConditionTests
    with FifoDeduplicationTests
    with MessageAttributesTests
    with TracingTests
    with HealthCheckTests

class AmazonJavaSdkV1TestSuite extends AmazonJavaMultiSdkTestSuite with SQSRestServerWithSdkV1Client
class AmazonJavaSdkV2TestSuite extends AmazonJavaMultiSdkTestSuite with SQSRestServerWithSdkV2Client
