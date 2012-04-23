package org.elasticmq.storage

import org.elasticmq.data.DataSource
import com.weiglewilczek.slf4s.Logging
import org.elasticmq.{MessageDoesNotExistException, QueueAlreadyExistsException, QueueDoesNotExistException}

trait StorageCommandExecutor extends Logging {
  def execute[R](command: StorageCommand[R]): R

  def executeToleratingAppliedCommands(command: StorageCommand[_]) {
    import scala.util.control.Exception._

    handling(classOf[QueueDoesNotExistException],
      classOf[QueueAlreadyExistsException],
      classOf[MessageDoesNotExistException])
      .by(logger.warn("Exception when applying command; a conflicting command was already applied to the storage. " +
      "This can happen e.g. when restoring state.", _))
      .apply {
      execute(command)
    }
  }

  def executeStateManagement[T](f: DataSource => T): T
  def shutdown()
}
