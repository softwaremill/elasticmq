package org.elasticmq.persistence.sql

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files
import java.sql.{DriverManager, SQLException}

class DBWriteDelayTest extends AnyFunSuite with Matchers {

  test("H2 URIs write each commit to the file, unless they set a write delay") {
    DB.withWriteDelay("jdbc:h2:/data/elasticmq-h2") shouldBe "jdbc:h2:/data/elasticmq-h2;WRITE_DELAY=0"
    DB.withWriteDelay("jdbc:h2:/data/db;MODE=MySQL") shouldBe "jdbc:h2:/data/db;MODE=MySQL;WRITE_DELAY=0"
    DB.withWriteDelay("jdbc:h2:/data/db;write_delay=500") shouldBe "jdbc:h2:/data/db;write_delay=500"
    DB.withWriteDelay("jdbc:postgresql://localhost/elasticmq") shouldBe "jdbc:postgresql://localhost/elasticmq"
  }

  test("a committed change survives a crash right after the commit") {
    Class.forName("org.h2.Driver")
    val path = Files.createTempDirectory("elasticmq-h2").resolve("db").toString

    val connection = DriverManager.getConnection(DB.withWriteDelay(s"jdbc:h2:$path"))
    try {
      connection.createStatement().execute("create table message (id varchar)")
      connection.createStatement().execute("insert into message values ('acknowledged')")
      // Closes the database without writing pending changes, as if the process had been killed
      try connection.createStatement().execute("SHUTDOWN IMMEDIATELY")
      catch { case _: SQLException => () }
    } finally connection.close()

    val reopened = DriverManager.getConnection(s"jdbc:h2:$path")
    try {
      val result = reopened.createStatement().executeQuery("select count(*) from message")
      result.next()
      result.getInt(1) shouldBe 1
    } finally reopened.close()
  }
}
