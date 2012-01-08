import sbt._
import Keys._

object Resolvers {
  val elasticmqResolvers = Seq(
    ScalaToolsSnapshots,
    "SotwareMill Public Releases" at "http://tools.softwaremill.pl/nexus/content/repositories/releases/")
}

object BuildSettings {
  import Resolvers._

  val buildSettings = Defaults.defaultSettings ++ Seq (
    organization  := "org.elasticmq",
    version       := "0.2-SNAPSHOT",
    scalaVersion  := "2.9.1",
    resolvers     := elasticmqResolvers,
    publishTo     <<= (version) { version: String =>
      val nexus = "http://tools.softwaremill.pl/nexus/content/repositories/"
      if (version.trim.endsWith("SNAPSHOT"))  Some("softwaremill-public-snapshots" at nexus + "snapshots/")
      else                                    Some("softwaremill-public-releases"  at nexus + "releases/")
    },
    credentials   += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    publishMavenStyle := true,
    // generating javadocs causes a weird error in Node:4 - type Client not found
    publishArtifact in (Compile, packageDoc) := false,
    parallelExecution in Test := false
  )
}

object Dependencies {
  val squeryl       = "org.squeryl"               %% "squeryl"              % "0.9.4"
  val h2            = "com.h2database"            % "h2"                    % "1.3.156"
  val c3p0          = "c3p0"                      % "c3p0"                  % "0.9.1.2"
  val jodaTime      = "joda-time"                 % "joda-time"             % "1.6.2" // when available use https://github.com/jorgeortiz85/scala-time
  val netty         = "org.jboss.netty"           % "netty"                 % "3.2.4.Final"

  val log4j         = "log4j"                     % "log4j"                 % "1.2.16"

  val scalatest     = "org.scalatest"             %% "scalatest"            % "1.6.1"         % "test"
  val mockito       = "org.mockito"               % "mockito-core"          % "1.7"           % "test"

  val apacheHttp    = "org.apache.httpcomponents" % "httpclient"            % "4.1.1"

  val typica        = "com.google.code.typica"    % "typica"                % "1.7-softwaremill-4"
  val amazonJavaSdk = "com.amazonaws"             % "aws-java-sdk"          % "1.2.15"

  val mysqlConnector = "mysql"                    % "mysql-connector-java"  % "5.1.12"

  val common = Seq(log4j)
  val testing = Seq(scalatest, mockito)
  val httpTesting = Seq(apacheHttp % "test")

  // To get the source run the <update-classifiers> task.
}

object ElasticMQBuild extends Build {
  import Dependencies._
  import BuildSettings._

  lazy val root: Project = Project(
    "root",
    file("."),
    settings = buildSettings
  ) aggregate(api, core, rest, restSqsTestingTypica)

  lazy val api: Project = Project(
    "api",
    file("api"),
    settings = buildSettings ++ Seq(libraryDependencies := Seq(jodaTime))
  )

  lazy val core: Project = Project(
    "core",
    file("core"),
    settings = buildSettings ++ Seq(libraryDependencies := Seq(squeryl, h2, c3p0, mysqlConnector % "test") ++ common ++ testing)
  ) dependsOn(api)

  lazy val rest: Project = Project(
    "rest",
    file("rest"),
    settings = buildSettings
  ) aggregate(restCore, restSqs)

  lazy val restCore: Project = Project(
    "rest-core",
    file("rest/rest-core"),
    settings = buildSettings ++ Seq(libraryDependencies := Seq(netty) ++ common ++ testing ++ httpTesting)
  )

  lazy val restSqs: Project = Project(
    "rest-sqs",
    file("rest/rest-sqs"),
    settings = buildSettings ++ Seq(libraryDependencies := Seq(mysqlConnector % "test") ++ common ++ testing ++ httpTesting)
  ) dependsOn(api, restCore, core % "test->compile")

  lazy val restSqsTestingTypica: Project = Project(
    "rest-sqs-testing-typica",
    file("rest/rest-sqs-testing-typica"),
    settings = buildSettings ++ Seq(libraryDependencies := Seq(typica, apacheHttp) ++ common ++ testing)
  ) dependsOn(restSqs, core % "test->compile")

  lazy val restSqsTestingAmazonJavaSdk: Project = Project(
    "rest-sqs-testing-amazon-java-sdk",
    file("rest/rest-sqs-testing-amazon-java-sdk"),
    settings = buildSettings ++ Seq(libraryDependencies := Seq(amazonJavaSdk) ++ common ++ testing)
  ) dependsOn(restSqs, core % "test->compile")
}
