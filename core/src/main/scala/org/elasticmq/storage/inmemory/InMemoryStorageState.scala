package org.elasticmq.storage.inmemory

import org.elasticmq.storage.interfaced.StorageState
import java.io.{OutputStream, InputStream}

class InMemoryStorageState extends StorageState {
  def dump(outputStream: OutputStream) {

  }

  def restore(inputStream: InputStream) {

  }
}
