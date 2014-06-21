addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.4")

resolvers ++= Seq(
  "less is" at "http://repo.lessis.me",
  "coda" at "http://repo.codahale.com")

addSbtPlugin("com.typesafe.sbt" % "sbt-pgp" % "0.8.2")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.11.2")

addSbtPlugin("com.gu" % "sbt-teamcity-test-reporting-plugin" % "1.5")

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation")