package org.elasticmq.storage.filelog

import java.io.File

case class FileLogConfiguration(storageDir: File, rotateLogsAfterCommandWritten: Int)
