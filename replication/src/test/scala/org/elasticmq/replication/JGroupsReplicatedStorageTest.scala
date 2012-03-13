package org.elasticmq.replication

import org.scalatest.matchers.MustMatchers
import org.scalatest.FunSuite
import org.elasticmq.storage.inmemory.InMemoryStorageCommandExecutor
import org.joda.time.{Duration, DateTime}
import org.elasticmq.data.{MessageData, QueueData}
import org.elasticmq.{MillisNextDelivery, MessageId, MillisVisibilityTimeout}
import org.elasticmq.storage.{StorageCommandExecutor, LookupMessageCommand, SendMessageCommand, CreateQueueCommand}

class JGroupsReplicatedStorageTest extends FunSuite with MustMatchers {
  def testWithStorageCluster(testName: String, clusterNodes: Int)(testFun: StorageCluster => Unit) {
    test(testName) {
      val storages = (1 to clusterNodes).map(_ => new InMemoryStorageCommandExecutor)
      val replicatedStorages = storages.map(new ReplicatedStorageConfigurator(_).start())
      val cluster = StorageCluster(storages, replicatedStorages)
      
      try {
        testFun(cluster)
      } finally {
        val result = cluster.replicatedStorages.flatMap(rs => try { rs.stop(); None } catch { case e => Some(e) })
        result match {
          case e :: _ => throw e
          case _ =>
        }
      }
    }
  }

  testWithStorageCluster("should replicate command", 2) { cluster =>
    // When
    cluster.master.execute(new CreateQueueCommand(QueueData("q1", MillisVisibilityTimeout(1000L),
      Duration.ZERO, new DateTime, new DateTime)))
    cluster.master.execute(new SendMessageCommand("q1", MessageData(MessageId("1"), "z",
      MillisNextDelivery(System.currentTimeMillis()), new DateTime)))
    
    // Then
    Thread.sleep(500L)

    cluster.storages.foreach(_.execute(LookupMessageCommand("q1", MessageId("1"))) must be ('defined))
  }
  
  case class StorageCluster(storages: Seq[StorageCommandExecutor], replicatedStorages: Seq[ReplicatedStorage]) {
    def master = replicatedStorages.find(_.isMaster).get
  }
}
