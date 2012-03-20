package org.elasticmq.storage.interfaced

import java.io.{InputStream, OutputStream}

trait StorageStateManager {
  def dump(outputStream: OutputStream)
  def restore(inputStream: InputStream)
}
