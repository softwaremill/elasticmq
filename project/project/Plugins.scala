import sbt._

object Plugins extends Build {
  // TeamCity reporting, see: https://github.com/guardian/sbt-teamcity-test-reporting-plugin
  lazy val plugins = Project("plugins", file("."))
    .dependsOn(
    uri("git://github.com/guardian/sbt-teamcity-test-reporting-plugin.git#1.1"))
}

