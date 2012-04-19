package org.elasticmq

import java.io.File
import util.Random

package object test {
  def timed(block: => Unit) = {
    val start = System.currentTimeMillis()
    block
    val end = System.currentTimeMillis()

    end-start
  }

  def createTempDir(): File = {
    val tempFile = File.createTempFile("temporary", "x")
    val tempFileParent = tempFile.getParent
    tempFile.delete()

    val tempDir = new File(tempFileParent, "tempDir" + new Random().nextInt())
    if (!tempDir.mkdir()) {
      throw new RuntimeException("Cannot create directory: " + tempDir.getAbsolutePath)
    }

    tempDir
  }

  def deleteDirRecursively(dir: File) {
    dir.listFiles().filter(_.isDirectory).foreach(deleteDirRecursively(_))
    dir.listFiles().filter(!_.isDirectory).foreach(_.delete())
    dir.delete()
  }
}