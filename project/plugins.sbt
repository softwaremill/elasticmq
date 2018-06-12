addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill" % "1.3.3")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.6")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

libraryDependencies += "com.amazonaws" % "aws-java-sdk-s3" % "1.11.343"

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation")
