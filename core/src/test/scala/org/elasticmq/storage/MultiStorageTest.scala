package org.elasticmq.storage

import inmemory._
import org.scalatest.matchers.MustMatchers
import org.scalatest._
import org.elasticmq._
import org.squeryl.adapters.H2Adapter
import org.elasticmq.storage.squeryl._
import org.elasticmq.test.DataCreationHelpers

trait MultiStorageTest extends FunSuite with MustMatchers with OneInstancePerTest with DataCreationHelpers {
  private case class StorageTestSetup(storageName: String,
                                      initialize: () => StorageCommandExecutor,
                                      shutdown: () => Unit)

  val squerylCommandExecutor = new SquerylStorageModule {}.storageCommandExecutor

  val squerylDBConfiguration = DBConfiguration(new H2Adapter,
    "jdbc:h2:mem:"+this.getClass.getName+";DB_CLOSE_DELAY=-1",
    "org.h2.Driver")

  private val setups: List[StorageTestSetup] =
    StorageTestSetup("Squeryl",
      () => {
        squerylCommandExecutor.modules.initializeSqueryl(squerylDBConfiguration);
        squerylCommandExecutor
      },
      () => squerylCommandExecutor.modules.shutdownSqueryl(squerylDBConfiguration.drop)) ::
    StorageTestSetup("In memory",
      () => new InMemoryStorageModule {}.storageCommandExecutor,
      () => ()) :: Nil

  private var storageCommandExecutor: StorageCommandExecutor = null

  private var currentSetup: StorageTestSetup = null
  
  private var _befores: List[() => Unit] = Nil

  def before(block: => Unit) {
    _befores = (() => block) :: _befores
  }

  abstract override protected def test(testName: String, testTags: Tag*)(testFun: => Unit) {
    for (setup <- setups) {
      super.test(testName+" using "+setup.storageName, testTags: _*) {
        currentSetup = setup

        try {
          newStorageCommandExecutor()
          testFun
        } finally {
          setup.shutdown()
          currentSetup = null
        }
      }
    }
  }
  
  def execute[R](command: StorageCommand[R]): R = storageCommandExecutor.execute(command)
  
  def newStorageCommandExecutor() {
    if (storageCommandExecutor != null) {
      currentSetup.shutdown()
    }
    
    _befores.foreach(_())
    storageCommandExecutor = currentSetup.initialize()
  }
}





