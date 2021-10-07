package org.elasticmq.rest.sqs.persisted

import org.elasticmq.MessagePersistenceConfig
import org.elasticmq.rest.sqs._

trait MessagePersistenceEnabledConfig {
  def messagePersistenceConfig: MessagePersistenceConfig = MessagePersistenceConfig(
    enabled = true,
    driverClass = "org.sqlite.JDBC",
    uri = "jdbc:sqlite:./elastimq.db",
    pruneDataOnInit = true)
}

class PersistedAmazonJavaSdkTest extends AmazonJavaSdkTestSuite with MessagePersistenceEnabledConfig
class PersistedFifoDeduplicationTests extends FifoDeduplicationTests with MessagePersistenceEnabledConfig
class PersistedMessageAttributesTests extends MessageAttributesTests with MessagePersistenceEnabledConfig
class PersistedReceiveMessageAttributesTest extends ReceiveMessageAttributesTest with MessagePersistenceEnabledConfig
class PersistedTracingTests extends TracingTests with MessagePersistenceEnabledConfig
