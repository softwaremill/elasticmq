import sbt._
import Keys._
import sbtassembly.Plugin._
import AssemblyKeys._

object BuildSettings {
  val buildSettings = Defaults.coreDefaultSettings ++ Seq (
    organization  := "org.elasticmq",
    version       := "0.9.0",
    scalaVersion  := "2.11.7",

    libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.0.5",

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
  ) ++ net.virtualvoid.sbt.graph.Plugin.graphSettings
}

object Dependencies {
  val jodaTime      = "joda-time"                 % "joda-time"             % "2.9"
  val jodaConvert   = "org.joda"                  % "joda-convert"          % "1.8.1"
  val config        = "com.typesafe"              % "config"                % "1.2.1"

  val scalalogging  = "com.typesafe.scala-logging" %% "scala-logging"       % "3.1.0"
  val logback       = "ch.qos.logback"            % "logback-classic"       % "1.1.3"
  val jclOverSlf4j  = "org.slf4j"                 % "jcl-over-slf4j"        % "1.7.12" // needed form amazon java sdk

  val scalatest     = "org.scalatest"             %% "scalatest"            % "2.2.5"
  val mockito       = "org.mockito"               % "mockito-core"          % "1.10.19"
  val awaitility    = "com.jayway.awaitility"     % "awaitility-scala"      % "1.6.5"

  val amazonJavaSdk = "com.amazonaws"             % "aws-java-sdk"          % "1.10.29" exclude ("commons-logging", "commons-logging")

  val akka2Version  = "2.3.14"
  val akka2Actor    = "com.typesafe.akka" %% "akka-actor"           % akka2Version
  val akka2Slf4j    = "com.typesafe.akka" %% "akka-slf4j"           % akka2Version
  val akka2Testkit  = "com.typesafe.akka" %% "akka-testkit"         % akka2Version % "test"
  val akka2Http     = "com.typesafe.akka" %% "akka-http-experimental" % "1.0"
  val akka2HttpTestkit = "com.typesafe.akka" %% "akka-http-testkit-experimental" % "1.0" % "test"

  val scalaAsync    = "org.scala-lang.modules" % "scala-async_2.11" % "0.9.5"

  val common = Seq(scalalogging)
}

object ElasticMQBuild extends Build {
  import Dependencies._
  import BuildSettings._

  lazy val root: Project = Project(
    "elasticmq-root",
    file("."),
    settings = buildSettings
  ) aggregate(commonTest, core, rest, server)

  lazy val commonTest: Project = Project(
    "elasticmq-common-test",
    file("common-test"),
    settings = buildSettings ++ Seq(
      libraryDependencies ++= Seq(scalatest, mockito, awaitility, logback),
      publishArtifact := false)
  )

  lazy val core: Project = Project(
    "elasticmq-core",
    file("core"),
    settings = buildSettings ++ Seq(libraryDependencies ++= Seq(jodaTime, jodaConvert, akka2Actor, akka2Testkit) ++ common)
  ) dependsOn(commonTest % "test")

  lazy val rest: Project = Project(
    "elasticmq-rest",
    file("rest"),
    settings = buildSettings
  ) aggregate(restSqs, restSqsTestingAmazonJavaSdk)

  lazy val restSqs: Project = Project(
    "elasticmq-rest-sqs",
    file("rest/rest-sqs"),
    settings = buildSettings ++
      Seq(libraryDependencies ++= Seq(akka2Actor, akka2Slf4j, akka2Http, akka2HttpTestkit, scalaAsync) ++ common)
  ) dependsOn(core, commonTest % "test")

  lazy val restSqsTestingAmazonJavaSdk: Project = Project(
    "elasticmq-rest-sqs-testing-amazon-java-sdk",
    file("rest/rest-sqs-testing-amazon-java-sdk"),
    settings = buildSettings ++ Seq(
      libraryDependencies ++= Seq(amazonJavaSdk, jclOverSlf4j) ++ common,
      publishArtifact := false)
  ) dependsOn(restSqs % "test->test")

  lazy val server: Project = Project(
    "elasticmq-server",
    file("server"),
    settings = buildSettings ++ CustomTasks.generateVersionFileSettings ++ assemblySettings ++ Seq(
      libraryDependencies ++= Seq(logback, config),
      mainClass in assembly := Some("org.elasticmq.server.Main")
    )
  ) dependsOn(core, restSqs, commonTest % "test")

  lazy val performanceTests: Project = Project(
    "elasticmq-performance-tests",
    file("performance-tests"),
    settings = buildSettings ++ Seq(
      libraryDependencies ++= Seq(amazonJavaSdk, jclOverSlf4j, logback) ++ common,
      publishArtifact := false
    )
  ) dependsOn(core, restSqs, commonTest % "test")
}

object CustomTasks {
  val generateVersionFileSettings = Seq(
    resourceGenerators in Compile += Def.task {
      val targetFile = (resourceManaged in Compile).value / "version"
      IO.write(targetFile, version.value.toString)
      Seq(targetFile)
    }.taskValue
  )
}
