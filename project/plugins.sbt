// required for sbt-github-release resolution
resolvers += "Era7 maven releases" at "https://s3-eu-west-1.amazonaws.com/releases.era7.com" 

addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill" % "1.4.2")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.9")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.23")

addSbtPlugin("ohnosequences" % "sbt-github-release" % "0.7.1")

libraryDependencies += "com.amazonaws" % "aws-java-sdk-s3" % "1.11.566"

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation")
