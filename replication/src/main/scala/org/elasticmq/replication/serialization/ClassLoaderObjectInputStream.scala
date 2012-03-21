package org.elasticmq.replication.serialization

import java.io.{ObjectStreamClass, ObjectInputStream, InputStream}
import org.elasticmq.Queue

class ClassLoaderObjectInputStream(classLoader: ClassLoader,
                                   inputStream: InputStream) extends ObjectInputStream(inputStream) {
  // By default using ElasticMQ's classloader
  def this(inputStream: InputStream) = this(classOf[Queue].getClassLoader, inputStream)
  
  override def resolveClass(objectStreamClass: ObjectStreamClass): Class[_] = {
    val clazz = Class.forName(objectStreamClass.getName, false, classLoader)
    if (clazz != null) clazz
    else super.resolveClass(objectStreamClass)
  }
}
