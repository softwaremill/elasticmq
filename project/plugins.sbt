// required for sbt-github-release resolution
resolvers += "Era7 maven releases" at "https://s3-eu-west-1.amazonaws.com/releases.era7.com"

val sbtSoftwaremillVersion = "1.9.15"
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-common" % sbtSoftwaremillVersion)
addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-publish" % sbtSoftwaremillVersion)

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.1.0")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.9.2")

addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.7")

libraryDependencies += "com.amazonaws" % "aws-java-sdk-s3" % "1.11.601"

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation")
