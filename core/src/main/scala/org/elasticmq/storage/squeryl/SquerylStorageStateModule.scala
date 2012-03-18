package org.elasticmq.storage.squeryl

import org.elasticmq.storage.interfaced.StorageState
import java.io.{OutputStream, InputStream}

trait SquerylStorageStateModule {
  class SquerylStorageState extends StorageState {
    def dump(outputStream: OutputStream) {

    }

    def restore(inputStream: InputStream) {

    }
  }

  def storageState = new SquerylStorageState
}