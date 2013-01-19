package org.elasticmq.replication.jgroups

import java.io.{OutputStream, InputStream}
import org.jgroups.{Message, MessageListener}
import com.typesafe.scalalogging.slf4j.Logging
import org.elasticmq.storage.StorageCommandExecutor
import org.elasticmq.replication.state.{StateRestorer, StateDumper}

class JGroupsStateTransferMessageListener(storage: StorageCommandExecutor) extends MessageListener with Logging {
  val stateDumper = new StateDumper(storage)
  val stateRestorer = new StateRestorer(storage)
  
  def receive(msg: Message) {}

  def getState(output: OutputStream) {
    logger.info("Getting state for state transfer")
    stateDumper.dump(output)
  }

  def setState(input: InputStream) {
    logger.info("Setting state from state transfer")
    stateRestorer.restore(input)
  }
}
