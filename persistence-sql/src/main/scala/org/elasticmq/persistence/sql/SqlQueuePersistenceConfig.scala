package org.elasticmq.persistence.sql

case class SqlQueuePersistenceConfig(
    enabled: Boolean = false,
    driverClass: String = "",
    uri: String = "",
    username: String = "",
    password: String = "",
    pruneDataOnInit: Boolean = false
)
