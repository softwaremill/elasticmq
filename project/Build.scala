import sbt._
import Keys._

object Resolvers {
  val elasticmqResolvers = Seq(ScalaToolsSnapshots)
}

object BuildSettings {
  import Resolvers._

  val buildSettings = Defaults.defaultSettings ++ Seq (
    organization  := "org.elasticmq",
    version       := "1.0",
    scalaVersion  := "2.9.0-1",
    resolvers     := elasticmqResolvers
  )
}

object Dependencies {
  val squeryl = "org.squeryl" %% "squeryl" % "0.9.4"
  val h2 = "com.h2database" % "h2" % "1.3.156"
  val scalatest = "org.scalatest" % "scalatest_2.9.0" % "1.6.1" % "test"

  //val squerylSrc = srcFor(squeryl)

  //def srcFor(artifact: ModuleID) = artifact % "sources" classifier "sources"
}

object ElasticMQBuild extends Build {
  import Dependencies._
  import BuildSettings._

  lazy val root: Project = Project("root", file("."), settings = buildSettings) aggregate(core)
  lazy val core: Project = Project("core", file("core"), settings = buildSettings ++ Seq(
    libraryDependencies := Seq(squeryl, h2, scalatest)))
}
