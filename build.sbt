import com.amazonaws.services.s3.model.PutObjectResult
import com.softwaremill.Publish.ossPublishSettings
import com.softwaremill.SbtSoftwareMillCommon.commonSmlBuildSettings
import com.typesafe.sbt.packager.docker.*
import sbt.Keys.javaOptions
import sbt.internal.util.complete.Parsers.spaceDelimited
import scoverage.ScoverageKeys.*

import scala.sys.process.Process

val v2_12 = "2.12.19"
val v2_13 = "2.13.13"
val v3 = "3.4.1"

lazy val resolvedScalaVersion =
  sys.env.get("SCALA_MAJOR_VERSION") match {
    case Some("2.12")      => v2_12
    case Some("2.13")      => v2_13
    case Some("3")         => v3
    case Some(unsupported) => throw new IllegalArgumentException(s"Unsupported SCALA_MAJOR_VERSION: $unsupported")
    case _                 => v2_13
  }

lazy val uiDirectory = settingKey[File]("Path to the ui project directory")
lazy val updateYarn = taskKey[Unit]("Update yarn")
lazy val yarnTask = inputKey[Unit]("Run yarn with arguments")
lazy val ensureDockerBuildx = taskKey[Unit]("Ensure that docker buildx configuration exists")
lazy val dockerBuildWithBuildx = taskKey[Unit]("Build docker images using buildx")

val config = "com.typesafe" % "config" % "1.4.3"
val pureConfig = "com.github.pureconfig" %% "pureconfig-core" % "0.17.6"
val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "2.3.0"

val scalalogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"
val logback = "ch.qos.logback" % "logback-classic" % "1.3.14"
val jclOverSlf4j = "org.slf4j" % "jcl-over-slf4j" % "2.0.16" // needed form amazon java sdk

val scalatest = "org.scalatest" %% "scalatest" % "3.2.19"
val awaitility = "org.awaitility" % "awaitility-scala" % "4.2.2"

val amazonJavaSdkSqs = "com.amazonaws" % "aws-java-sdk-sqs" % "1.12.699" exclude ("commons-logging", "commons-logging")
val amazonJavaV2SdkSqs = "software.amazon.awssdk" % "sqs" % "2.25.60"

val pekkoVersion = "1.0.3"
val pekkoHttpVersion = "1.0.1"
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
  "com.github.rssh" %% "shim-scala-async-dotty-cps-async" % "0.9.21" // allows cross compilation w/o changes in source code

val scalikeJdbc = "org.scalikejdbc" %% "scalikejdbc" % "4.2.1"
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
  sonatypeProfileName := "org.elasticmq",
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
  // native-server project is only used for building docker with graalvm native image
  .aggregate(commonTest, core, rest, persistence, server, ui)

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
  .dependsOn(core % "compile->compile;test->test", commonTest % "test")

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
  .enablePlugins(JavaServerAppPackaging, DockerPlugin)
  .settings(buildSettings)
  .settings(dockerBuildxSettings)
  .settings(generateVersionFileSettings)
  .settings(uiSettings)
  .settings(
    Seq(
      name := "elasticmq-server",
      libraryDependencies ++= Seq(logback),
      Compile / unmanagedResourceDirectories += { baseDirectory.value / ".." / "ui" / "build" },
      assembly := assembly.dependsOn(yarnTask.toTask(" build")).value,
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
      },
      // docker
      dockerExposedPorts := Seq(9324, 9325),
      dockerBaseImage := "openjdk:11-jdk-stretch",
      Docker / packageName := "elasticmq",
      dockerUsername := Some("softwaremill"),
      dockerUpdateLatest := {
        !version.value.toLowerCase.contains("rc")
      },
      Universal / javaOptions ++= Seq("-Dconfig.file=/opt/elasticmq.conf"),
      Docker / mappings ++= Seq(
        (baseDirectory.value / "docker" / "elasticmq.conf") -> "/opt/elasticmq.conf"
      ),
      Docker / publishLocal := (Docker / publishLocal)
        .dependsOn(yarnTask.toTask(" build"))
        .value,
      dockerCommands += Cmd(
        "COPY",
        "--from=stage0",
        s"--chown=${(Docker / daemonUser).value}:root",
        "/opt/elasticmq.conf",
        "/opt"
      ),
      dockerExposedVolumes += "/data"
    )
  )
  .dependsOn(core, restSqs, persistenceFile, persistenceSql, commonTest % "test")

val graalVmVersion = "22.1.0"
val graalVmTag = s"ol8-java11-$graalVmVersion"
val graalVmBaseImage = "ghcr.io/graalvm/graalvm-ce"
val alpineVersion = "3.18"

lazy val nativeServer: Project = (project in file("native-server"))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(buildSettings)
  .settings(uiSettings)
  .settings(dockerBuildxSettings)
  .settings(
    Seq(
      name := "elasticmq-native-server",
      libraryDependencies ++= Seq(
        "org.graalvm.nativeimage" % "svm" % graalVmVersion % "compile-internal"
      ),
      publish / skip := true,
      Compile / mainClass := Some("org.elasticmq.server.Main"),
      dockerPermissionStrategy := DockerPermissionStrategy.None,
      // We want to utilize `docker buildx` to create native image in the first stage
      // and then create the target docker image based on Alpine with just the native image, configuration and ui files.
      // GraalVMNativeImagePlugin does support such scenario because it uses `docker run` to build the native image
      // and then simply copies the artifact to the result docker image, which makes it difficult to create ARM native image.
      dockerCommands := {
        val commands = dockerCommands.value
        val binaryName = name.value
        val className = (Compile / mainClass).value.getOrElse(sys.error("Could not find a main class."))

        // This is copied from DockerPlugin - it seems like the layers with id contain the runtime jars
        // and the layers without id contain custom files defined in Docker / mappings
        val layerMappings = (Docker / dockerLayerMappings).value
        val layerIdsAscending = layerMappings.map(_.layerId).distinct.sortWith { (a, b) =>
          // Make the None (unspecified) layer the last layer
          a.getOrElse(Int.MaxValue) < b.getOrElse(Int.MaxValue)
        }

        val nativeImageCmd = Seq(
          "/usr/local/bin/native-image",
          "-cp",
          layerMappings.map(_.path).filter(_.endsWith(".jar")).mkString(":"),
          "-H:IncludeResources=.*conf",
          "-H:IncludeResources=version",
          "-H:IncludeResources=.*\\.properties",
          "-H:+ReportExceptionStackTraces",
          "-H:-ThrowUnsafeOffsetErrors",
          s"-H:Name=$binaryName",
          "--enable-http",
          "--enable-https",
          "--enable-url-protocols=https,http",
          "--report-unsupported-elements-at-runtime",
          "--initialize-at-build-time=scala.Symbol$",
          "--allow-incomplete-classpath",
          "--install-exit-handlers",
          "--no-fallback",
          "--verbose",
          "--static",
          className
        )

        // Create build stage based on GraalVM image
        val graalVmBuildCommands = Seq(
          Cmd("FROM", s"$graalVmBaseImage:$graalVmTag"),
          Cmd("WORKDIR", "/opt/graalvm"),
          ExecCmd("RUN", "gu", "install", "native-image"),
          ExecCmd("RUN", "sh", "-c", "ln -s /opt/graalvm-ce-*/bin/native-image /usr/local/bin/native-image")
        ) ++ layerIdsAscending.map(layerId => {
          val files = "opt"
          val path = layerId.map(i => s"$i/$files").getOrElse(s"$files")
          Cmd("COPY", s"$path /$files")
        }) ++ Seq(Cmd("RUN", nativeImageCmd: _*))

        // We want to include the image generated in stage0 around the COPY commands
        val lastIndexOfCopy = commands.lastIndexWhere {
          case Cmd("COPY", _) => true
          case _              => false
        }

        val (front, back) = commands.splitAt(lastIndexOfCopy + 1)

        val updatedCommands = front.filter {
          case Cmd("COPY", args @ _*) =>
            // We do not want to include jar layers (the one with number based root path: /1, /2, /3, etc.)
            args.head.startsWith("opt")
          case _ => true
        } ++ Seq(
          Cmd("COPY", "--from=0", s"/opt/graalvm/$binaryName", s"/opt/elasticmq/bin/$binaryName"),
          ExecCmd("RUN", "apk", "add", "--no-cache", "tini")
        ) ++ back

        graalVmBuildCommands ++ updatedCommands
      },
      Docker / defaultLinuxInstallLocation := "/opt/elasticmq",
      Docker / mappings ++= Seq(
        (baseDirectory.value / ".." / "server" / "docker" / "elasticmq.conf") -> "/opt/elasticmq.conf",
        (baseDirectory.value / ".." / "server" / "src" / "main" / "resources" / "logback.xml") -> "/opt/logback.xml"
      ) ++ sbt.Path.contentOf(baseDirectory.value / ".." / "ui" / "build").map { case (file, mapping) =>
        (file, "/opt/elasticmq/" + mapping)
      },
      dockerEntrypoint := Seq(
        "/sbin/tini", // tini makes it possible to kill the process with Cmd+C/Ctrl+C when running in interactive mode (-it)
        "--",
        "/opt/elasticmq/bin/elasticmq-native-server",
        "-Dconfig.file=/opt/elasticmq.conf",
        "-Dlogback.configurationFile=/opt/logback.xml"
      ),
      dockerUpdateLatest := {
        !version.value.toLowerCase.contains("rc")
      },
      dockerExposedPorts := Seq(9324, 9325),
      dockerUsername := Some("softwaremill"),
      dockerExposedVolumes += "/data",
      dockerBaseImage := s"alpine:$alpineVersion",
      Docker / packageName := "elasticmq-native",
      Compile / packageBin := (Compile / packageBin)
        .dependsOn(yarnTask.toTask(" build"))
        .value
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

lazy val dockerBuildxSettings = Seq(
  ensureDockerBuildx := {
    if (Process("docker buildx inspect multi-arch-builder").! == 1) {
      Process("docker buildx create --use --name multi-arch-builder", baseDirectory.value).!
    }
  },
  dockerBuildWithBuildx := {
    streams.value.log("Building and pushing image with Buildx")
    dockerAliases.value.foreach(alias =>
      Process(
        "docker buildx build --platform=linux/arm64,linux/amd64 --push -t " + alias + " .",
        baseDirectory.value / "target" / "docker" / "stage"
      ).!
    )
  },
  Docker / publish := Def
    .sequential(
      Docker / publishLocal,
      ensureDockerBuildx,
      dockerBuildWithBuildx
    )
    .value
)

lazy val uiSettings = Seq(
  uiDirectory := baseDirectory.value.getParentFile / "ui",
  updateYarn := {
    streams.value.log("Updating npm/yarn dependencies")
    haltOnCmdResultError(Process("yarn install", uiDirectory.value).!)
  },
  yarnTask := {
    val taskName = spaceDelimited("<arg>").parsed.mkString(" ")
    updateYarn.value
    val localYarnCommand = "yarn " + taskName
    def runYarnTask() = Process(localYarnCommand, uiDirectory.value).!
    streams.value.log("Running yarn task: " + taskName)
    haltOnCmdResultError(runYarnTask())
  }
)

lazy val ui = (project in file("ui"))
  .settings(buildSettings)
  .settings(uiSettings)
  .settings(
    Test / test := (Test / test).dependsOn(yarnTask.toTask(" test:ci")).value,
    Compile / compile := {
      yarnTask.toTask(" build").value
      (Compile / compile).value
    },
    cleanFiles += baseDirectory.value / "build",
    Compile / unmanagedResourceDirectories += baseDirectory.value / "build",
    publish / skip := true
  )

def haltOnCmdResultError(result: Int) {
  if (result != 0) {
    throw new Exception("Build failed.")
  }
}
