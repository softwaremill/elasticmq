package org.elasticmq.replication

import org.scalatest.matchers.MustMatchers
import org.scalatest.FunSuite
import org.elasticmq.storage.inmemory.InMemoryStorageCommandExecutor
import org.joda.time.{Duration, DateTime}
import org.elasticmq.data.{MessageData, QueueData}
import org.elasticmq.storage.{StorageCommandExecutor, LookupMessageCommand, SendMessageCommand, CreateQueueCommand}
import org.elasticmq.{NodeAddress, MillisNextDelivery, MessageId, MillisVisibilityTimeout}
import org.elasticmq.test._

import com.jayway.awaitility.Awaitility._
import com.jayway.awaitility.scala.AwaitilitySupport
import com.weiglewilczek.slf4s.Logging

class JGroupsReplicatedStorageTest extends FunSuite with MustMatchers with AwaitilitySupport with Logging {
  def testWithStorageCluster(testName: String,
                             clusterNodes: Int,
                             commandReplicationMode: CommandReplicationMode = DoNotWaitReplicationMode)
                            (testFun: StorageCluster => Unit) {
    test(testName) {
      var i = 0
      def newNodeAddress() = { i+=1; NodeAddress("node"+i) }
      
      val storages = (1 to clusterNodes).map(_ => new InMemoryStorageCommandExecutor)      
      val replicatedStorages = storages.map(
        new ReplicatedStorageConfigurator(_, newNodeAddress(), commandReplicationMode).start())
      val cluster = StorageCluster(storages, replicatedStorages)

      try {
        val clusterFormation = timed { await until replicatedStorages.forall(_.masterAddress.isDefined) }
        logger.info("cluster formed in " + clusterFormation)

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

    Thread.sleep(100L)

    // Then
    cluster.replicatedStorages.count(_.isMaster) must be (1)
    cluster.storages.foreach(_.execute(LookupMessageCommand("q1", MessageId("1"))) must be ('defined))
  }

  testWithStorageCluster("should replicate command waiting for other nodes", 3, WaitForAllReplicationMode) { cluster =>
  // When
    cluster.master.execute(new CreateQueueCommand(QueueData("q1", MillisVisibilityTimeout(1000L),
      Duration.ZERO, new DateTime, new DateTime)))
    cluster.master.execute(new SendMessageCommand("q1", MessageData(MessageId("1"), "z",
      MillisNextDelivery(System.currentTimeMillis()), new DateTime)))

    // Then
    cluster.replicatedStorages.count(_.isMaster) must be (1)
    cluster.storages.foreach(_.execute(LookupMessageCommand("q1", MessageId("1"))) must be ('defined))
  }

  testWithStorageCluster("all nodes should point to the same master", 3) { cluster =>
    val masterAddress = cluster.master.address
    cluster.replicatedStorages.foreach {
      _.masterAddress must be (Some(masterAddress))
    }
  }
  
  case class StorageCluster(storages: Seq[StorageCommandExecutor], replicatedStorages: Seq[ReplicatedStorage]) {
    def master = replicatedStorages.find(_.isMaster).get
  }
}
