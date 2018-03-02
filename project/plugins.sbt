addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.6")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation")
