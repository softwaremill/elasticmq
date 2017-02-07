import sbt._
import Keys._
import sbtassembly.AssemblyKeys._

object BuildSettings {
  val buildSettings = Defaults.coreDefaultSettings ++ Seq (
    organization  := "org.elasticmq",
    version       := "0.13.2",
    scalaVersion  := "2.11.8",
    crossScalaVersions := Seq(scalaVersion.value, "2.12.1"),

    libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.0.6",

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
}

object Dependencies {
  val jodaTime      = "joda-time"                 % "joda-time"             % "2.9.7"
  val jodaConvert   = "org.joda"                  % "joda-convert"          % "1.8.1"
  val config        = "com.typesafe"              % "config"                % "1.3.1"

  val scalalogging  = "com.typesafe.scala-logging" %% "scala-logging"       % "3.5.0"
  val logback       = "ch.qos.logback"            % "logback-classic"       % "1.1.9"
  val jclOverSlf4j  = "org.slf4j"                 % "jcl-over-slf4j"        % "1.7.22" // needed form amazon java sdk

  val scalatest     = "org.scalatest"             %% "scalatest"            % "3.0.1"
  val awaitility    = "com.jayway.awaitility"     % "awaitility-scala"      % "1.7.0"

  val amazonJavaSdk = "com.amazonaws"             % "aws-java-sdk"          % "1.11.84" exclude ("commons-logging", "commons-logging")

  val akkaVersion      = "2.4.16"
  val akkaHttpVersion  = "10.0.2"
  val akka2Actor       = "com.typesafe.akka" %% "akka-actor"           % akkaVersion
  val akka2Slf4j       = "com.typesafe.akka" %% "akka-slf4j"           % akkaVersion
  val akka2Testkit     = "com.typesafe.akka" %% "akka-testkit"         % akkaVersion % "test"
  val akka2Http        = "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion
  val sprayJson        = "io.spray" %% "spray-json"                    % "1.3.3"
  val akka2HttpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test"

  val scalaAsync = "org.scala-lang.modules" %% "scala-async" % "0.9.6"

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
      libraryDependencies ++= Seq(scalatest, awaitility, logback),
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
      Seq(libraryDependencies ++= Seq(akka2Actor, akka2Slf4j, akka2Http, sprayJson, akka2HttpTestkit, scalaAsync) ++ common)
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
    settings = buildSettings ++ CustomTasks.generateVersionFileSettings ++ Seq(
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
