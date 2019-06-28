import java.nio.file.{Files, StandardCopyOption}

import com.amazonaws.services.s3.model.PutObjectResult
import com.softwaremill.Publish.Release.updateVersionInDocs
import com.typesafe.sbt.packager.docker.Cmd
import sbt.Keys.credentials
import sbtrelease.ReleaseStateTransformations._
import scoverage.ScoverageKeys._

val buildSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "org.elasticmq",
  scalaVersion := "2.12.8",
  crossScalaVersions := Seq(scalaVersion.value, "2.11.12"),
  libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.2.0",
  dependencyOverrides := akka25Overrides,
  parallelExecution := false,
  sonatypeProfileName := "org.elasticmq",
  scalafmtOnCompile := true,
  // github release
  ghreleaseRepoOrg := "adamw",
  ghreleaseRepoName := "elasticmq",
  ghreleaseNotes := (_ => ""),
  ghreleaseTitle := (tagName => tagName.toString),
  ghreleaseIsPrerelease := (_ => false),
  ghreleaseAssets := Nil,
  // workaround for: https://github.com/sbt/sbt/issues/692
  fork in Test := true,
  releaseProcess := {
    val uploadAssembly: ReleaseStep = ReleaseStep(
      action = { st: State =>
        val extracted = Project.extract(st)
        val (st2, _) = extracted.runTask(assembly in server, st)
        val (st3, _) = extracted.runTask(s3Upload in server, st2)
        st3
      }
    )

    val uploadDocker: ReleaseStep = ReleaseStep(
      action = { st: State =>
        val extracted = Project.extract(st)
        val (st2, _) = extracted.runTask(publish in Docker in server, st)
        st2
      }
    )

    Seq(
      checkSnapshotDependencies,
      inquireVersions,
      // publishing locally so that the pgp password prompt is displayed early
      // in the process
      releaseStepCommand("publishLocalSigned"),
      runClean,
      runTest,
      setReleaseVersion,
      uploadDocker,
      uploadAssembly,
      updateVersionInDocs(organization.value),
      commitReleaseVersion,
      tagRelease,
      publishArtifacts,
      releaseStepCommand("sonatypeReleaseAll"),
      setNextVersion,
      commitNextVersion,
      pushChanges,
      releaseStepCommand("core/githubRelease")
    )
  }
)

val jodaTime = "joda-time" % "joda-time" % "2.10.2"
val jodaConvert = "org.joda" % "joda-convert" % "2.2.1"
val config = "com.typesafe" % "config" % "1.3.4"

val scalalogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
val jclOverSlf4j = "org.slf4j" % "jcl-over-slf4j" % "1.7.26" // needed form amazon java sdk

val scalatest = "org.scalatest" %% "scalatest" % "3.0.8"
val awaitility = "org.awaitility" % "awaitility-scala" % "3.1.6"

val amazonJavaSdk = "com.amazonaws" % "aws-java-sdk" % "1.11.566" exclude ("commons-logging", "commons-logging")

val scalaGraph = "org.scala-graph" %% "graph-core" % "1.12.5"

val akkaVersion = "2.5.23"
val akkaHttpVersion = "10.1.8"
val akka2Actor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
val akka2Slf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
val akka2Streams = "com.typesafe.akka" %% "akka-stream" % akkaVersion
val akka2Testkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
val akka2Http = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
val sprayJson = "io.spray" %% "spray-json" % "1.3.5"
val akka2HttpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test"

val scalaAsync = "org.scala-lang.modules" %% "scala-async" % "0.9.7"

val common = Seq(scalalogging)

val akka25Overrides =
  Seq( // override the 2.4.x transitive dependency from Akka HTTP
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion
  )

// see https://github.com/scala/scala-dist/pull/181/files
val s3Upload = TaskKey[PutObjectResult]("s3-upload", "Uploads files to an S3 bucket.")

lazy val root: Project = (project in file("."))
  .settings(buildSettings)
  .settings(name := "elasticmq-root")
  .aggregate(commonTest, core, rest, server, nativeServer)

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
  .settings(Seq(
    name := "elasticmq-rest-sqs",
    libraryDependencies ++= Seq(akka2Actor,
                                akka2Slf4j,
                                akka2Http,
                                akka2Streams,
                                sprayJson,
                                akka2Testkit,
                                akka2HttpTestkit,
                                scalaAsync) ++ common
  ))
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
  .enablePlugins(JavaServerAppPackaging, DockerPlugin)
  .settings(buildSettings)
  .settings(generateVersionFileSettings)
  .settings(Seq(
    name := "elasticmq-server",
    libraryDependencies ++= Seq(logback, config, scalaGraph),
    mainClass in assembly := Some("org.elasticmq.server.Main"),
    coverageMinimum := 52,
    // s3 upload
    s3Upload := {
      import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials, DefaultAWSCredentialsProviderChain}
      import com.amazonaws.services.s3.AmazonS3ClientBuilder
      import com.amazonaws.services.s3.model.{CannedAccessControlList, PutObjectRequest}

      val bucketName = "softwaremill-public"
      val creds = Credentials.forHost(credentials.value, bucketName + ".s3.amazonaws.com")

      val awsCreds = creds match {
        case Some(cred) => new AWSStaticCredentialsProvider(new BasicAWSCredentials(cred.userName, cred.passwd))
        case None       => new DefaultAWSCredentialsProviderChain
      }

      val client = AmazonS3ClientBuilder.standard().withCredentials(awsCreds).withRegion("eu-west-1").build()

      val log = streams.value.log
      val v = version.value

      val source = (assemblyOutputPath in assembly).value
      val targetObjectName = s"elasticmq-server-$v.jar"

      log.info("Uploading " + source.getAbsolutePath + " as " + targetObjectName)

      client.putObject(new PutObjectRequest(bucketName, targetObjectName, source)
        .withCannedAcl(CannedAccessControlList.PublicRead))
    },
    /*
    Format:
    realm=Amazon S3
    host=softwaremill-public.s3.amazonaws.com
    user=[AWS key id]
    password=[AWS secret key]
     */
    credentials += Credentials(Path.userHome / ".s3_elasticmq_credentials"),
    // docker
    dockerExposedPorts := Seq(9324),
    dockerBaseImage := "openjdk:8u212-b04-jdk-stretch",
    packageName in Docker := "elasticmq",
    dockerUsername := Some("softwaremill"),
    dockerUpdateLatest := true,
    javaOptions in Universal ++= Seq("-Dconfig.file=/opt/elasticmq.conf"),
    mappings in Docker += (baseDirectory.value / "docker" / "elasticmq.conf") -> "/opt/elasticmq.conf",
    dockerCommands += Cmd("COPY",
                          "--from=stage0",
                          s"--chown=${(daemonUser in Docker).value}:root",
                          "/opt/elasticmq.conf",
                          "/opt")
  ))
  .dependsOn(core, restSqs, commonTest % "test")

val dockerGraalvmNative = taskKey[Unit]("Create a docker image containing a binary build with GraalVM's native-image.")
val dockerGraalvmNativeImageName =
  settingKey[String]("Name of the generated docker image, containing the native binary.")

lazy val nativeServer: Project = (project in file("native-server"))
  .enablePlugins(JavaServerAppPackaging, DockerPlugin)
  .settings(buildSettings)
  .settings(Seq(
    name := "elasticmq-native-server",
    libraryDependencies += "com.oracle.substratevm" % "svm" % "19.0.2" % Provided,
    mainClass in assembly := Some("org.elasticmq.server.Main"),
    mappings in Docker += (baseDirectory.value / ".." / "server" / "docker" / "elasticmq.conf") -> "/opt/elasticmq.conf",
    //
    dockerGraalvmNativeImageName := "elasticmq-native",
    dockerGraalvmNative := {
      val log = streams.value.log

      val stageDir = target.value / "native-docker" / "stage"
      stageDir.mkdirs()

      // copy all jars to the staging directory
      val cpDir = stageDir / "cp"
      cpDir.mkdirs()

      val classpathJars = (mappings in Universal).value.map(_._1).filter(_.name.endsWith(".jar"))
      classpathJars.foreach(cpJar =>
        Files.copy(cpJar.toPath, (cpDir / cpJar.name).toPath, StandardCopyOption.REPLACE_EXISTING))

      val resultDir = stageDir / "result"
      resultDir.mkdirs()
      val resultName = "out"

      val className = (mainClass in Compile in server).value.getOrElse(sys.error("Could not find a main class."))

      val nativeImageConfDir = baseDirectory.value

      /*
      Settings based on:
      https://blog.softwaremill.com/small-fast-docker-images-using-graalvms-native-image-99c0bc92e70b
      https://github.com/vmencik/akka-graal-native
      https://github.com/Jotschi/vertx-graalvm-native-image-test/blob/master/build.sh
      https://github.com/sdeleuze/graal-issues/tree/master/logback
       */
      val runNativeImageCommand = Seq(
        "docker",
        "run",
        "--rm",
        "-v",
        s"${cpDir.getAbsolutePath}:/opt/cp",
        "-v",
        s"${resultDir.getAbsolutePath}:/opt/graalvm",
        "-v",
        s"${(nativeImageConfDir / "reflectconf").getAbsolutePath}:/opt/reflectconf",
        "graalvm-native-image",
        "-cp",
        "/opt/cp/*",
        "--static",
        // "--report-unsupported-elements-at-runtime",
        "--allow-incomplete-classpath", // for logback
        s"-H:Name=$resultName",
        "-H:ReflectionConfigurationFiles=/opt/reflectconf/logback.json",
        "-H:ReflectionConfigurationFiles=/opt/reflectconf/akka.json",
        "-H:IncludeResources=.*conf",
        "-H:IncludeResources=version",
        "-H:IncludeResources=.*\\.properties",
        "-H:IncludeResources='org/joda/time/tz/data/.*'",
        "--enable-url-protocols=https,http",
        "--initialize-at-build-time",
        "--no-fallback",
        className
      )

      log.info("Running native-image using the 'graalvm-native-image' docker container")
      log.info(s"Running: ${runNativeImageCommand.mkString(" ")}")

      sys.process.Process(runNativeImageCommand, resultDir) ! streams.value.log match {
        case 0 => resultDir / resultName
        case r => sys.error(s"Failed to run docker, exit status: " + r)
      }

      val buildContainerCommand = Seq(
        "docker",
        "build",
        "-t",
        "softwaremill/" + dockerGraalvmNativeImageName.value + ":latest",
        "-t",
        "softwaremill/" + dockerGraalvmNativeImageName.value + ":" + version.value,
        "-f",
        (nativeImageConfDir / "run-native-image" / "Dockerfile").getAbsolutePath,
        resultDir.absolutePath
      )
      log.info("Building the container with the generated native image")
      log.info(s"Running: ${buildContainerCommand.mkString(" ")}")

      sys.process.Process(buildContainerCommand, resultDir) ! streams.value.log match {
        case 0 => resultDir / resultName
        case r => sys.error(s"Failed to run docker, exit status: " + r)
      }

      log.info(s"Build image ${dockerGraalvmNativeImageName.value}")
    }
  ))
  .dependsOn(server)

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
