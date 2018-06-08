import scoverage.ScoverageKeys._

val buildSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "org.elasticmq",
  scalaVersion := "2.12.6",
  crossScalaVersions := Seq(scalaVersion.value, "2.11.12"),
  libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.1.0",
  dependencyOverrides := akka25Overrides,
  parallelExecution := false,
  // workaround for: https://github.com/sbt/sbt/issues/692
  fork in Test := true
)

val jodaTime = "joda-time" % "joda-time" % "2.10"
val jodaConvert = "org.joda" % "joda-convert" % "2.1"
val config = "com.typesafe" % "config" % "1.3.3"

val scalalogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"
val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
val jclOverSlf4j = "org.slf4j" % "jcl-over-slf4j" % "1.7.25" // needed form amazon java sdk

val scalatest = "org.scalatest" %% "scalatest" % "3.0.5"
val awaitility = "com.jayway.awaitility" % "awaitility-scala" % "1.7.0"

val amazonJavaSdk = "com.amazonaws" % "aws-java-sdk" % "1.11.343" exclude ("commons-logging", "commons-logging")

val scalaGraph = "org.scala-graph" %% "graph-core" % "1.12.5"

val akkaVersion = "2.5.13"
val akkaHttpVersion = "10.1.2"
val akka2Actor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
val akka2Slf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
val akka2Streams = "com.typesafe.akka" %% "akka-stream" % akkaVersion
val akka2Testkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
val akka2Http = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
val sprayJson = "io.spray" %% "spray-json" % "1.3.4"
val akka2HttpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test"

val scalaAsync = "org.scala-lang.modules" %% "scala-async" % "0.9.7"

val common = Seq(scalalogging)

val akka25Overrides =
  Seq( // override the 2.4.x transitive dependency from Akka HTTP
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion
  )

lazy val root: Project = (project in file("."))
  .settings(buildSettings)
  .settings(name := "elasticmq-root")
  .aggregate(commonTest, core, rest, server)

lazy val commonTest: Project = (project in file("common-test"))
  .settings(buildSettings)
  .settings(name := "elasticmq-common-test")
  .settings(Seq(libraryDependencies ++= Seq(scalatest, awaitility, logback), publishArtifact := false))

lazy val core: Project = (project in file("core"))
  .settings(buildSettings)
  .settings(
    Seq(name := "elasticmq-core",
        libraryDependencies ++= Seq(jodaTime, jodaConvert, akka2Actor, akka2Testkit) ++ common,
        coverageMinimum := 94))
  .dependsOn(commonTest % "test")

lazy val rest: Project = (project in file("rest"))
  .settings(buildSettings)
  .settings(name := "elasticmq-rest")
  .aggregate(restSqs, restSqsTestingAmazonJavaSdk)

lazy val restSqs: Project = (project in file("rest/rest-sqs"))
  .settings(buildSettings)
  .settings(
    Seq(name := "elasticmq-rest-sqs",
        libraryDependencies ++= Seq(akka2Actor,
                                    akka2Slf4j,
                                    akka2Http,
                                    akka2Streams,
                                    sprayJson,
                                    akka2HttpTestkit,
                                    scalaAsync) ++ common))
  .dependsOn(core, commonTest % "test")

lazy val restSqsTestingAmazonJavaSdk: Project =
  (project in file("rest/rest-sqs-testing-amazon-java-sdk"))
    .settings(buildSettings)
    .settings(
      Seq(name := "elasticmq-rest-sqs-testing-amazon-java-sdk",
          libraryDependencies ++= Seq(amazonJavaSdk, jclOverSlf4j) ++ common,
          publishArtifact := false))
    .dependsOn(restSqs % "test->test")

lazy val server: Project = (project in file("server"))
  .settings(buildSettings)
  .settings(generateVersionFileSettings)
  .settings(Seq(
    name := "elasticmq-server",
    libraryDependencies ++= Seq(logback, config, scalaGraph),
    mainClass in assembly := Some("org.elasticmq.server.Main"),
    coverageMinimum := 52
  ))
  .dependsOn(core, restSqs, commonTest % "test")

lazy val performanceTests: Project = (project in file("performance-tests"))
  .settings(buildSettings)
  .settings(
    Seq(
      name := "elasticmq-performance-tests",
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
