addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.11.2")

addSbtPlugin("com.gu" % "sbt-teamcity-test-reporting-plugin" % "1.5")

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.0.1")

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation")
