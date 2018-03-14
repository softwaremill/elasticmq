import sbt._
import Keys._
import scoverage.ScoverageKeys._

val buildSettings = Defaults.coreDefaultSettings ++ Seq (
  organization  := "org.elasticmq",
  version       := "0.13.9",
  scalaVersion  := "2.12.4",
  crossScalaVersions := Seq(scalaVersion.value, "2.11.11"),

  libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.1.0",

  dependencyOverrides := akka25Overrides,

  // Sonatype OSS deployment
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    val (name, url) = if (isSnapshot.value) ("snapshots", nexus + "content/repositories/snapshots")
    else ("releases", nexus + "service/local/staging/deploy/maven2")
    Some(name at url)
  },
  credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomExtra := <scm>
    <url>git@github.com:adamw/elasticmq.git</url>
    <connection>scm:git:git@github.com:adamw/elasticmq.git</connection>
  </scm>
    <developers>
      <developer>
        <id>adamw</id>
        <name>Adam Warski</name>
        <url>http://www.warski.org</url>
      </developer>
    </developers>,
  parallelExecution := false,
  // workaround for: https://github.com/sbt/sbt/issues/692
  fork in Test := true,
  scalacOptions ++= List("-unchecked", "-encoding", "UTF8"),
  homepage      := Some(new java.net.URL("http://www.elasticmq.org")),
  licenses      := ("Apache2", new java.net.URL("http://www.apache.org/licenses/LICENSE-2.0.txt")) :: Nil
)

val jodaTime      = "joda-time"                 % "joda-time"             % "2.9.9"
val jodaConvert   = "org.joda"                  % "joda-convert"          % "1.8.1"
val config        = "com.typesafe"              % "config"                % "1.3.1"

val scalalogging  = "com.typesafe.scala-logging" %% "scala-logging"       % "3.5.0"
val logback       = "ch.qos.logback"            % "logback-classic"       % "1.2.3"
val jclOverSlf4j  = "org.slf4j"                 % "jcl-over-slf4j"        % "1.7.25" // needed form amazon java sdk

val scalatest     = "org.scalatest"             %% "scalatest"            % "3.0.3"
val awaitility    = "com.jayway.awaitility"     % "awaitility-scala"      % "1.7.0"

val amazonJavaSdk = "com.amazonaws"             % "aws-java-sdk"          % "1.11.295" exclude ("commons-logging", "commons-logging")

val scalaGraph    = "org.scala-graph"           %% "graph-core"           % "1.11.5"

val akkaVersion      = "2.5.11"
val akkaHttpVersion  = "10.1.0"
val akka2Actor       = "com.typesafe.akka" %% "akka-actor"           % akkaVersion
val akka2Slf4j       = "com.typesafe.akka" %% "akka-slf4j"           % akkaVersion
val akka2Streams     = "com.typesafe.akka" %% "akka-stream"          % akkaVersion
val akka2Testkit     = "com.typesafe.akka" %% "akka-testkit"         % akkaVersion % "test"
val akka2Http        = "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion
val sprayJson        = "io.spray" %% "spray-json"                    % "1.3.3"
val akka2HttpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test"

val scalaAsync = "org.scala-lang.modules" %% "scala-async" % "0.9.6"

val common = Seq(scalalogging)

val akka25Overrides = Seq( // override the 2.4.x transitive dependency from Akka HTTP
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion)

lazy val root: Project = (project in file("."))
  .settings(buildSettings)
  .aggregate(commonTest, core, rest, server)

lazy val commonTest: Project = (project in file("common-test"))
  .settings(buildSettings)
  .settings(Seq(
    libraryDependencies ++= Seq(scalatest, awaitility, logback),
    publishArtifact := false))

lazy val core: Project = (project in file("core"))
  .settings(buildSettings)
  .settings(Seq(
    libraryDependencies ++= Seq(jodaTime, jodaConvert, akka2Actor, akka2Testkit) ++ common,
    coverageMinimum := 94))
  .dependsOn(commonTest % "test")

lazy val rest: Project = (project in file("rest"))
  .settings(buildSettings)
  .aggregate(restSqs, restSqsTestingAmazonJavaSdk)

lazy val restSqs: Project = (project in file("rest/rest-sqs"))
  .settings(buildSettings)
  .settings(Seq(libraryDependencies ++= Seq(akka2Actor, akka2Slf4j, akka2Http, akka2Streams, sprayJson, akka2HttpTestkit, scalaAsync) ++ common))
  .dependsOn(core, commonTest % "test")

lazy val restSqsTestingAmazonJavaSdk: Project = (project in file("rest/rest-sqs-testing-amazon-java-sdk"))
  .settings(buildSettings)
  .settings(Seq(
    libraryDependencies ++= Seq(amazonJavaSdk, jclOverSlf4j) ++ common,
    publishArtifact := false))
  .dependsOn(restSqs % "test->test")

lazy val server: Project = (project in file("server"))
  .settings(buildSettings)
  .settings(generateVersionFileSettings)
  .settings(Seq(
    libraryDependencies ++= Seq(logback, config, scalaGraph),
    mainClass in assembly := Some("org.elasticmq.server.Main"),
    coverageMinimum := 52
  )
  )
  .dependsOn(core, restSqs, commonTest % "test")

lazy val performanceTests: Project = (project in file("performance-tests"))
  .settings(buildSettings)
  .settings(Seq(
    libraryDependencies ++= Seq(amazonJavaSdk, jclOverSlf4j, logback) ++ common,
    publishArtifact := false
  ))
  .dependsOn(core, restSqs, commonTest % "test")

val generateVersionFileSettings = Seq(
  resourceGenerators in Compile += Def.task {
    val targetFile = (resourceManaged in Compile).value / "version"
    IO.write(targetFile, version.value.toString)
    Seq(targetFile)
  }.taskValue
)
