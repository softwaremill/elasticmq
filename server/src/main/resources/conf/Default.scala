import org.elasticmq.server.ElasticMQServerConfig
import org.elasticmq.replication._
import org.elasticmq.storage.squeryl.DBConfiguration

new ElasticMQServerConfig {
  // For a full reference on possible options see the ElasticMQServerConfig class. Head version:
  // https://github.com/adamw/elasticmq/blob/master/server/src/main/scala/org/elasticmq/server/ElasticMQServerConfig.scala

  // Configure main message storage, by default in memory.
  // E.g. for mysql database-backed:
  // storage = DatabaseStorage(DBConfiguration.mysql("elasticmq", "user", "password"))

  // Logging commands to a file (journalling) only makes sense for the in-memory storage.
  // To turn off:
  // fileLog.enabled = false
  // To store data is another directory:
  // fileLog.storageDir = new File("/path/to/a/directory")

  // To change the outside visible address of this ElasticMQ node:
  // nodeAddress = NodeAddress(host = "host", port = 9324)

  // To enable replication:
  // replication.enabled = true
  // By default replication expects at least 3 nodes. Change if you are going to have a different number of nodes, as
  // the messaging server only works if at least half of the configured number of nodes is alive. This is needed to
  // ensure data consistency.
  // replication.numberOfNodes = 5
  // If the nodes can't be discovered via multicast, you will need to provide the initial set of memebers manually:
  // replication.nodeDiscovery = TCP(List("host1:9324", "host2:9333", ...))

  // To bind REST SQS to a different host/port:
  // restSqs.bindPort = 9325
}