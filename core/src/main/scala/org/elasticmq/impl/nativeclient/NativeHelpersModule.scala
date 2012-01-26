package org.elasticmq.impl.nativeclient

import org.elasticmq.impl.NowModule
import java.util.concurrent.atomic.AtomicReference
import org.elasticmq.MillisNextDelivery

trait NativeHelpersModule {
  this: NowModule =>

  trait WithLazyAtomicData[T <: AnyRef] {
    private val currentData: AtomicReference[T] = new AtomicReference[T]()

    def initData: T

    def data = currentData.get() match {
      case null => {
        val theData = initData
        currentData.set(theData)
        theData
      }
      case theData => theData
    }

    def data_=(theData: T) { currentData.set(theData) }

    def clearData() { currentData.set(null) }
  }

  def computeNextDelivery(delta: Long): MillisNextDelivery = {
    MillisNextDelivery(now + delta)
  }
}