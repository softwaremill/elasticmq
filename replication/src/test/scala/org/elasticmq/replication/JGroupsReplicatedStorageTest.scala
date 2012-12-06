package org.elasticmq.replication

import org.scalatest.matchers.MustMatchers
import org.scalatest.FunSuite
import org.elasticmq.storage.inmemory.InMemoryStorage
import org.joda.time.{Duration, DateTime}
import org.elasticmq.data.{MessageData, QueueData}
import org.elasticmq.test._

import com.jayway.awaitility.Awaitility._
import com.jayway.awaitility.scala.AwaitilitySupport
import com.weiglewilczek.slf4s.Logging
import org.elasticmq._
import scala.collection.mutable.ArrayBuffer
import org.elasticmq.storage._
import org.jgroups.stack.ProtocolStack
import java.util.concurrent.TimeUnit
import org.jgroups.protocols.{FD_ALL, UDP, DISCARD}
import org.jgroups.JChannel

class JGroupsReplicatedStorageTest extends FunSuite with MustMatchers with AwaitilitySupport with Logging {
  def testWithStorageCluster(testName: String,
                             clusterNodes: Int,
                             commandReplicationMode: CommandReplicationMode = DoNotWaitReplicationMode,
                             numberOfNodes: Option[Int] = None)
                            (testFun: (ClusterConfigurator, StorageCluster) => Unit) {
    test(testName) {
      val cluster = new StorageCluster()

      val clusterConfigurator = new ClusterConfigurator(commandReplicationMode,
        numberOfNodes.getOrElse(clusterNodes),
        cluster)

      (1 to clusterNodes).map(_ => clusterConfigurator.startNewNode())

      try {
        cluster.awaitUntilFormed()

        testFun(clusterConfigurator, cluster)
      } finally {
        val result = cluster.replicatedStorages.flatMap(rs => try { rs.shutdown(); None } catch { case e => Some(e) }).toList
        result match {
          case e :: _ => throw e
          case _ =>
        }
      }
    }
  }

  class StorageCluster() {
    val allStorages = new ArrayBuffer[(StorageCommandExecutor, ReplicatedStorage)]

    def storages = allStorages.map(_._1)

    def replicatedStorages = allStorages.map(_._2)

    def master = replicatedStorages.find(_.isMaster).get

    def awaitUntilFormed() {
      val clusterFormation = timed { await until replicatedStorages.forall(_.masterAddress.isDefined) }
      logger.info("cluster formed in " + clusterFormation)
    }
  }

  class ClusterConfigurator(commandReplicationMode: CommandReplicationMode,
                            numberOfNodes: Int,
                            cluster: StorageCluster) {
    var i = 0

    def newNodeAddress() = { i += 1; NodeAddress(host = "node"+i) }

    def startNewNode(createJChannel: () => JChannel = () => new JChannel()) = {
      val storage = new InMemoryStorage
      val replicatedStorage = new ReplicatedStorageConfigurator(storage,
        newNodeAddress(),
        commandReplicationMode,
        numberOfNodes,
        createJChannel).start()

      cluster.allStorages += ((storage, replicatedStorage))

      (storage, replicatedStorage)
    }
  }

  testWithStorageCluster("should replicate command", 2) { (_, cluster) =>
    // When
    cluster.master.execute(new CreateQueueCommand(QueueData("q1", MillisVisibilityTimeout(1000L),
      Duration.ZERO, new DateTime, new DateTime)))
    cluster.master.execute(new SendMessageCommand("q1", MessageData(MessageId("1"), None, "z",
      MillisNextDelivery(System.currentTimeMillis()), new DateTime)))

    // We need to wait for the message to be replicated & applied
    Thread.sleep(100L)

    // Then
    cluster.replicatedStorages.count(_.isMaster) must be (1)
    cluster.storages.foreach(_.execute(LookupMessageCommand("q1", MessageId("1"))) must be ('defined))
  }

  testWithStorageCluster("should replicate command waiting for other nodes", 3, WaitForAllReplicationMode) { (_, cluster) =>
    // When
    sendExampleData(cluster.master)

    // Then
    cluster.replicatedStorages.count(_.isMaster) must be (1)
    cluster.storages.foreach(_.execute(LookupMessageCommand("q1", MessageId("1"))) must be ('defined))
  }

  testWithStorageCluster("all nodes should point to the same master", 3) { (_, cluster) =>
    val masterAddress = cluster.master.address
    cluster.replicatedStorages.foreach {
      _.masterAddress must be (Some(masterAddress))
    }
  }

  testWithStorageCluster("should replicate state when a new node starts", 2, WaitForAllReplicationMode) {
    (clusterConfigurator, cluster) =>

    // Given
    sendExampleData(cluster.master)

    // When
    val (newStorage, newReplicatedStorage) = clusterConfigurator.startNewNode()
    await until { newReplicatedStorage.masterAddress == Some(cluster.master.address) }
    sendExampleData(cluster.master, "q2")

    // Then
    // Both new and old data should be found on the new storage
    newStorage.execute(LookupMessageCommand("q1", MessageId("1"))) must be ('defined)
    newStorage.execute(LookupMessageCommand("q2", MessageId("1"))) must be ('defined)
  }

  testWithStorageCluster("should allow operations only when n/2+1 nodes are active", 2, WaitForAllReplicationMode,
    numberOfNodes = Some(5)) {
    (clusterConfigurator, cluster) =>

    // When
    intercept[NodeIsNotActiveException] { cluster.master.execute(createQueueCommand("qx")) }

    // Starting new node - cluster should become active
    val (_, newReplicatedStorage) = clusterConfigurator.startNewNode()
    await until { newReplicatedStorage.masterAddress == Some(cluster.master.address) }

    // This should succeed now
    cluster.master.execute(createQueueCommand("qx"))
  }

  testWithStorageCluster("should replicate state if a node becomes inactive and then active again", 0,
    WaitForAllReplicationMode, numberOfNodes = Some(3)) {
    (clusterConfigurator, cluster) =>

    // Given
    def createJChannelWithQuickFD = {
      val jchannel = new JChannel()
      val fdAll = jchannel.getProtocolStack.findProtocol(classOf[FD_ALL]).asInstanceOf[FD_ALL]
      fdAll.setInterval(500L)
      fdAll.setTimeout(1000L)
      jchannel
    }

    def createJChannelWithDiscard = {
      val jchannel = createJChannelWithQuickFD
      val discardProtocol = new DISCARD()
      jchannel.getProtocolStack.insertProtocol(discardProtocol, ProtocolStack.ABOVE, classOf[UDP])

      (jchannel, discardProtocol)
    }

    clusterConfigurator.startNewNode(createJChannelWithQuickFD _)
    clusterConfigurator.startNewNode(createJChannelWithQuickFD _)

    cluster.awaitUntilFormed()

    // When
    val (newChannel, discardProtocol) = createJChannelWithDiscard
    val (newStorage, _) = clusterConfigurator.startNewNode(() => newChannel)

    discardProtocol.setDiscardAll(true)

    logger.info("Waiting until the member is removed")
    waitAtMost(20, TimeUnit.SECONDS) until { cluster.master.clusterState.currentNumberOfNodes == 2 }

    logger.info("Executing a command")
    cluster.master.execute(createQueueCommand("qy"))

    logger.info("Bringing the partitioned member back & waiting")
    discardProtocol.setDiscardAll(false)
    waitAtMost(20, TimeUnit.SECONDS) until { cluster.master.clusterState.currentNumberOfNodes == 3 }

    // Then
    logger.info("State should be replicated")
    await until { newStorage.execute(LookupQueueCommand("qy")).isDefined }
  }

  def sendExampleData(storage: ReplicatedStorage, queueName: String = "q1") {
    storage.execute(createQueueCommand(queueName))
    storage.execute(new SendMessageCommand(queueName, MessageData(MessageId("1"), None, "z",
      MillisNextDelivery(System.currentTimeMillis()), new DateTime)))
  }

  def createQueueCommand(queueName: String) = new CreateQueueCommand(QueueData(queueName,
    MillisVisibilityTimeout(1000L), Duration.ZERO, new DateTime, new DateTime))
}
