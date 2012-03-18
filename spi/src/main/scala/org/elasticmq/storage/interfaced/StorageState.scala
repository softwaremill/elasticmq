package org.elasticmq.storage.interfaced

import java.io.{InputStream, OutputStream}

trait StorageState {
  def dump(outputStream: OutputStream)
  def restore(inputStream: InputStream)
}
