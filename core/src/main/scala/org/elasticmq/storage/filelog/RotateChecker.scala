package org.elasticmq.storage.filelog

import javax.annotation.concurrent.NotThreadSafe

@NotThreadSafe
private[filelog] class RotateChecker(configuration: FileLogConfiguration) {
  private var commandsWrittenSinceLastRotate = 0

  def shouldRotate(newCommandsWritten: Int) = {
    commandsWrittenSinceLastRotate += newCommandsWritten

    if (commandsWrittenSinceLastRotate > configuration.rotateLogsAfterCommandWritten) {
      true
    } else {
      false
    }
  }

  def reset() {
    commandsWrittenSinceLastRotate = 0
  }

  def update(commandsCount: Int) {
    commandsWrittenSinceLastRotate = commandsCount
  }
}
