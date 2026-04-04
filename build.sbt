import com.amazonaws.services.s3.model.PutObjectResult
import com.softwaremill.Publish.ossPublishSettings
import com.softwaremill.SbtSoftwareMillCommon.commonSmlBuildSettings
import sbt.Keys.javaOptions
import sbt.internal.util.complete.Parsers.spaceDelimited
import scoverage.ScoverageKeys.*

import scala.sys.process.Process

val v2_12 = "2.12.20"
val v2_13 = "2.13.18"
val v3 = "3.3.7"

lazy val resolvedScalaVersion =
  sys.env.get("SCALA_MAJOR_VERSION") match {
    case Some("2.12")      => v2_12
    case Some("2.13")      => v2_13
    case Some("3")         => v3
    case Some(unsupported) => throw new IllegalArgumentException(s"Unsupported SCALA_MAJOR_VERSION: $unsupported")
    case _                 => v2_13
  }

val config = "com.typesafe" % "config" % "1.4.6"
val pureConfig = "com.github.pureconfig" %% "pureconfig-core" % "0.17.8"
val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "2.4.0"

val scalalogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6"
val logback = "ch.qos.logback" % "logback-classic" % "1.3.16"
val jclOverSlf4j = "org.slf4j" % "jcl-over-slf4j" % "2.0.17" // needed form amazon java sdk

val scalatest = "org.scalatest" %% "scalatest" % "3.2.19"
val awaitility = "org.awaitility" % "awaitility-scala" % "4.3.0"

val amazonJavaSdkSqs = "com.amazonaws" % "aws-java-sdk-sqs" % "1.12.699" exclude ("commons-logging", "commons-logging")
val amazonJavaV2SdkSqs = "software.amazon.awssdk" % "sqs" % "2.25.60"

val pekkoVersion = "1.5.0"
val pekkoHttpVersion = "1.3.0"
val pekkoActor = "org.apache.pekko" %% "pekko-actor" % pekkoVersion
val pekkoSlf4j = "org.apache.pekko" %% "pekko-slf4j" % pekkoVersion
val pekkoStreams = "org.apache.pekko" %% "pekko-stream" % pekkoVersion
val pekkoTestkit = "org.apache.pekko" %% "pekko-testkit" % pekkoVersion % "test"
val pekkoHttp = "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion
val sprayJson = "io.spray" %% "spray-json" % "1.3.6"
val pekkoHttpSprayJson = "org.apache.pekko" %% "pekko-http-spray-json" % pekkoHttpVersion
val pekkoHttpTestkit = "org.apache.pekko" %% "pekko-http-testkit" % pekkoHttpVersion % "test"

val scala2Async = "org.scala-lang.modules" %% "scala-async" % "1.0.1"
val scala3Async =
  "com.github.rssh" %% "shim-scala-async-dotty-cps-async" % "0.9.23" // allows cross compilation w/o changes in source code

val scalikeJdbc = "org.scalikejdbc" %% "scalikejdbc" % "4.3.5"
val h2 = "com.h2database" % "h2" % "2.2.224"

val common = Seq(scalalogging)

val pekko100verrides =
  Seq( // override transitive dependency from Pekko HTTP
    "org.apache.pekko" %% "pekko-stream" % pekkoVersion,
    "org.apache.pekko" %% "pekko-stream-testkit" % pekkoVersion
  )

val buildSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "org.elasticmq",
  scmInfo := Some(
    ScmInfo(url("https://github.com/softwaremill/elasticmq"), "scm:git@github.com:softwaremill/elasticmq.git")
  ),
  scalaVersion := resolvedScalaVersion,
  crossScalaVersions := List(v2_12, v2_13, v3),
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) => Seq("-Xtarget:8")
      case _            => Seq("-Xasync", "-target:jvm-1.8")
    }
  },
  libraryDependencies += scalaXml,
  dependencyOverrides := pekko100verrides,
  parallelExecution := false,
  // workaround for: https://github.com/sbt/sbt/issues/692
  Test / fork := true,
  assembly / assemblyMergeStrategy := {
    case PathList(ps @ _*) if ps.last == "module-info.class"    => MergeStrategy.first
    case PathList(ps @ _*) if ps.last == "reflect-config.json"  => MergeStrategy.first
    case PathList(ps @ _*) if ps.last == "resource-config.json" => MergeStrategy.first
    case x                                                      => (assembly / assemblyMergeStrategy).value(x)
  }
)

// see https://github.com/scala/scala-dist/pull/181/files
val s3Upload = TaskKey[PutObjectResult]("s3-upload", "Uploads files to an S3 bucket.")

lazy val root: Project = (project in file("."))
  .enablePlugins(GitVersioning)
  .settings(buildSettings)
  .settings(name := "elasticmq-root", publish / skip := true)
  // we want to build the main jar using java 8, but native-server requires java 11, so it's built separately
  // native-server project is only used for building the native Docker image with GraalVM
  .aggregate(commonTest, core, rest, persistence, server)

lazy val commonTest: Project = (project in file("common-test"))
  .settings(buildSettings)
  .settings(name := "elasticmq-common-test")
  .settings(Seq(libraryDependencies ++= Seq(scalatest, awaitility, logback), publish / skip := true))

lazy val core: Project = (project in file("core"))
  .settings(buildSettings)
  .settings(
    Seq(
      name := "elasticmq-core",
      libraryDependencies ++= Seq(pekkoActor, pekkoTestkit) ++ common,
      coverageMinimumStmtTotal := 94
    )
  )
  .dependsOn(commonTest % "test")

lazy val persistence: Project = (project in file("persistence"))
  .settings(buildSettings)
  .settings(name := "elasticmq-persistence", publishArtifact := false)
  .aggregate(persistenceCore, persistenceFile, persistenceSql)

lazy val persistenceCore: Project = (project in file("persistence/persistence-core"))
  .settings(buildSettings)
  .settings(
    Seq(
      name := "elasticmq-persistence-core",
      libraryDependencies ++= Seq(
        pekkoActor,
        pekkoSlf4j,
        config,
        pekkoTestkit,
        CrossVersion.partialVersion(scalaVersion.value) match {
          case Some((3, _)) => scala3Async
          case _            => scala2Async
        }
      ) ++ common
    )
  )
  .dependsOn(core % "compile->compile;test->test", commonTest % "test")

lazy val persistenceFile: Project = (project in file("persistence/persistence-file"))
  .settings(buildSettings)
  .settings(
    Seq(
      name := "elasticmq-persistence-file",
      libraryDependencies ++= Seq(pureConfig) ++ common
    )
  )
  .dependsOn(persistenceCore, commonTest % "test")

lazy val persistenceSql: Project = (project in file("persistence/persistence-sql"))
  .settings(buildSettings)
  .settings(
    Seq(
      name := "elasticmq-persistence-sql",
      libraryDependencies ++= Seq(
        sprayJson,
        scalikeJdbc,
        h2
      ) ++ common
    )
  )
  .dependsOn(persistenceCore, commonTest % "test")

lazy val rest: Project = (project in file("rest"))
  .settings(buildSettings)
  .settings(name := "elasticmq-rest")
  .aggregate(restSqs, restSqsTestingAmazonJavaSdk)

lazy val restSqs: Project = (project in file("rest/rest-sqs"))
  .settings(buildSettings)
  .settings(
    Seq(
      name := "elasticmq-rest-sqs",
      libraryDependencies ++= Seq(
        pekkoActor,
        pekkoSlf4j,
        pekkoHttp,
        pekkoStreams,
        sprayJson,
        pekkoHttpSprayJson,
        pekkoTestkit,
        pekkoHttpTestkit,
        CrossVersion.partialVersion(scalaVersion.value) match {
          case Some((3, _)) => scala3Async
          case _            => scala2Async
        }
      ) ++ common
    )
  )
  .dependsOn(core % "compile->compile;test->test", persistenceFile, persistenceSql, commonTest % "test")

lazy val restSqsTestingAmazonJavaSdk: Project =
  (project in file("rest/rest-sqs-testing-amazon-java-sdk"))
    .settings(buildSettings)
    .settings(
      Seq(
        name := "elasticmq-rest-sqs-testing-amazon-java-sdk",
        libraryDependencies ++= Seq(
          amazonJavaSdkSqs,
          amazonJavaV2SdkSqs,
          jclOverSlf4j
        ) ++ common,
        publish / skip := true
      )
    )
    .dependsOn(restSqs % "test->test", persistenceFile % "test", persistenceSql % "test")

lazy val server: Project = (project in file("server"))
  .settings(buildSettings)
  .settings(generateVersionFileSettings)
  .settings(
    Seq(
      name := "elasticmq-server",
      libraryDependencies ++= Seq(logback),
      assembly / mainClass := Some("org.elasticmq.server.Main"),
      coverageMinimumStmtTotal := 52,
      // s3 upload
      s3Upload := {
        import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
        import com.amazonaws.services.s3.AmazonS3ClientBuilder
        import com.amazonaws.services.s3.model.{CannedAccessControlList, PutObjectRequest}

        val bucketName = "softwaremill-public"

        val awsCreds =
          new AWSStaticCredentialsProvider(new BasicAWSCredentials(sys.env("S3_USER"), sys.env("S3_PASSWORD")))

        val client = AmazonS3ClientBuilder.standard().withCredentials(awsCreds).withRegion("eu-west-1").build()

        val log = streams.value.log
        val v = version.value

        val source = (assembly / assemblyOutputPath).value
        val targetObjectName = s"elasticmq-server-$v.jar"

        log.info("Uploading " + source.getAbsolutePath + " as " + targetObjectName)

        client.putObject(
          new PutObjectRequest(bucketName, targetObjectName, source)
            .withCannedAcl(CannedAccessControlList.PublicRead)
        )
      }
    )
  )
  .dependsOn(core, restSqs)

val graalVmVersion = "22.1.0"

lazy val nativeServer: Project = (project in file("native-server"))
  .settings(buildSettings)
  .settings(
    Seq(
      name := "elasticmq-native-server",
      libraryDependencies ++= Seq(
        "org.graalvm.nativeimage" % "svm" % graalVmVersion % "compile-internal"
      ),
      publish / skip := true,
      Compile / mainClass := Some("org.elasticmq.server.Main")
    )
  )
  .dependsOn(server)

lazy val performanceTests: Project = (project in file("performance-tests"))
  .settings(buildSettings)
  .settings(
    Seq(
      name := "elasticmq-performance-tests",
      libraryDependencies ++= Seq(amazonJavaSdkSqs, jclOverSlf4j, logback) ++ common,
      publish / skip := true
    )
  )
  .dependsOn(core, restSqs, commonTest % "test")

val generateVersionFileSettings = Seq(
  Compile / resourceGenerators += Def.task {
    val targetFile = (Compile / resourceManaged).value / "version"
    IO.write(targetFile, version.value.toString)
    Seq(targetFile)
  }.taskValue
)
