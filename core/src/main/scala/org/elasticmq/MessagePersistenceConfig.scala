package org.elasticmq

case class MessagePersistenceConfig(enabled: Boolean = false,
                                    driverClass: String = "",
                                    uri: String = "",
                                    username: String = "",
                                    password: String = "",
                                    pruneDataOnInit: Boolean = false)
