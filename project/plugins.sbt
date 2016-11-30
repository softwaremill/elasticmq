addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")

addSbtPlugin("com.gu" % "sbt-teamcity-test-reporting-plugin" % "1.5")

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation")
