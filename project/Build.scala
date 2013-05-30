import sbt._
import Keys._

object BuildSettings {
  import ls.Plugin._

  val buildSettings = Defaults.defaultSettings ++ Seq (
    organization  := "org.elasticmq",
    version       := "0.7.0-SNAPSHOT",
    scalaVersion  := "2.10.1",

    resolvers += "spray repo" at "http://repo.spray.io", // TODO
    resolvers += "nightly spray repo" at "http://nightlies.spray.io/", // TODO

    // Continuations
    autoCompilerPlugins := true,
    libraryDependencies <+= scalaVersion {
      v => compilerPlugin("org.scala-lang.plugins" % "continuations" % v)
    },
    scalacOptions += "-P:continuations:enable",

    // Sonatype OSS deployment
    publishTo <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    credentials   += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    pomExtra := (
      <scm>
        <url>git@github.com:adamw/elasticmq.git</url>
        <connection>scm:git:git@github.com:adamw/elasticmq.git</connection>
      </scm>
      <developers>
        <developer>
          <id>adamw</id>
          <name>Adam Warski</name>
          <url>http://www.warski.org</url>
        </developer>
      </developers>),

    parallelExecution := false,
    // workaround for: https://github.com/sbt/sbt/issues/692
    fork in Test := true,
    scalacOptions += "-unchecked",
    homepage      := Some(new java.net.URL("http://www.elasticmq.org")),
    licenses      := ("Apache2", new java.net.URL("http://www.apache.org/licenses/LICENSE-2.0.txt")) :: Nil
  ) ++ net.virtualvoid.sbt.graph.Plugin.graphSettings ++ lsSettings ++ Seq (
    (LsKeys.tags in LsKeys.lsync) := Seq("elasticmq", "messaging", "aws",
      "amazon", "sqs", "embedded", "message queue"),
    (externalResolvers in LsKeys.lsync) := Seq(
      "softwaremill-public-releases" at "http://tools.softwaremill.pl/nexus/content/repositories/releases"),
    (description in LsKeys.lsync) :=
      "Akka-based message queue server with an Amazon SQS compatible interface. " +
        "Can run embedded (great for testing applications which use SQS), or as a stand-alone server."
  )
}

object Dependencies {
  val jodaTime      = "joda-time"                 % "joda-time"             % "2.1"
  val jodaConvert   = "org.joda"                  % "joda-convert"          % "1.2"
  val config        = "com.typesafe"              % "config"                % "1.0.0"

  val scalalogging  = "com.typesafe"              %% "scalalogging-slf4j"   % "1.0.1"
  val logback       = "ch.qos.logback"            % "logback-classic"       % "1.0.9"
  val jclOverSlf4j  = "org.slf4j"                 % "jcl-over-slf4j"        % "1.7.2" // needed form amazon java sdk

  val scalatest     = "org.scalatest"             %% "scalatest"            % "1.9.1"
  val mockito       = "org.mockito"               % "mockito-core"          % "1.9.5"
  val awaitility    = "com.jayway.awaitility"     % "awaitility-scala"      % "1.3.4"

  val amazonJavaSdk = "com.amazonaws"             % "aws-java-sdk"          % "1.4.3" exclude ("commons-logging", "commons-logging")

  val akka2Version          = "2.1.2"
  val akka2Actor            = "com.typesafe.akka" %% "akka-actor"           % akka2Version
  val akka2Slf4j            = "com.typesafe.akka" %% "akka-slf4j"           % akka2Version
  val akka2Dataflow         = "com.typesafe.akka" %% "akka-dataflow"        % akka2Version
  val akka2Testkit          = "com.typesafe.akka" %% "akka-testkit"         % akka2Version % "test"

  val sprayVersion          = "1.1-20130516" //"1.1-M7"
  val sprayCan              = "io.spray"          %   "spray-can"          % sprayVersion
  val sprayRouting          = "io.spray"          %   "spray-routing"      % sprayVersion
  val sprayTestkit          = "io.spray"          %   "spray-testkit"      % sprayVersion % "test"

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
    settings = buildSettings ++ Seq(libraryDependencies ++= Seq(akka2Actor, akka2Dataflow, sprayCan, sprayRouting, sprayTestkit)
      ++ common)
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
    settings = buildSettings ++ CustomTasks.distributionSettings ++ CustomTasks.generateVersionFileSettings ++
      Seq(libraryDependencies ++= Seq(logback, config))
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
  implicit def str2pimped(s: String) = new {
    def bold = scala.Console.BOLD + s + scala.Console.RESET
    def green = scala.Console.GREEN + s + scala.Console.RESET
  }

  // Main settings & tasks
  val distributionName = SettingKey[String]("distribution-name", "Name of the distribution directory")
  val distributionDirectory = SettingKey[File]("distribution-directory", "The distribution directory")
  val distributionLibDirectory = SettingKey[File]("distribution-lib-directory", "The distribution library directory")
  val distributionBinDirectory = SettingKey[File]("distribution-bin-directory", "The distribution binary directory")
  val distributionConfDirectory = SettingKey[File]("distribution-conf-directory", "The distribution configuration directory")

  val distributionClean = TaskKey[Unit]("distribution-clean", "Remove previous distribution.")
  val distribution = TaskKey[Unit]("distribution", "Create a distribution containing startup script and all jars.")

  // Helper tasks
  val distributionCopyExternalDependencies = TaskKey[Set[File]]("distribution-copy-external-dependencies", "Copies the external dependencies to the distribution directory")
  val distributionCopyInternalDependencies = TaskKey[Set[File]]("distribution-copy-internal-dependencies", "Copies the internal dependencies to the distribution directory")
  val distributionCopyBin = TaskKey[Set[File]]("distribution-copy-bin", "Copy binaries (scripts).")
  val distributionCopyConf = TaskKey[Set[File]]("distribution-copy-conf", "Copy configuration.")
  val distributionCopyDocs = TaskKey[Set[File]]("distribution-copy-docs", "Copy documentation.")

  val projectDependenciesClosure = TaskKey[Set[ProjectRef]]("project-dependency-closure", "Calculates the closure of the project's dependencies, including transitive ones.")

  val distributionSettings = Seq(
    distributionName <<= (version) { (v) => "elasticmq-" + v },

    distributionDirectory <<= (target, distributionName) { (t, n) => t / "distribution" / n },

    distributionLibDirectory <<= (distributionDirectory) { (dd) => dd / "lib" },

    distributionBinDirectory <<= (distributionDirectory) { (dd) => dd / "bin" },

    distributionConfDirectory <<= (distributionDirectory) { (dd) => dd / "conf" },

    distributionCopyExternalDependencies <<= (distributionLibDirectory, externalDependencyClasspath in Compile) map { (dld, edc) =>
      IO.copy(edc.files.map(f => (f, dld / f.getName)))
    },

    projectDependenciesClosure <<= (thisProjectRef, state) map { (thr, s) =>
      val structure = Project.structure(s)

      def isCompileConfiguration(configuration: Option[String]): Boolean = {
        configuration.map(_.contains("compile->compile")).getOrElse(true)
      }

      def projectWithTransitiveDependencies(root: ProjectRef, acc: Set[ProjectRef]): Set[ProjectRef] = {
        val dependencies = Project.getProject(root, structure).toList.flatMap(_.dependencies
          // We only want compile dependencies
          .filter(cpDep => isCompileConfiguration(cpDep.configuration))
          .map(_.project))

        dependencies.foldLeft(acc)((newAcc, dep) => {
          if (newAcc.contains(dep)) newAcc else projectWithTransitiveDependencies(dep, newAcc + dep)
        })
      }

      projectWithTransitiveDependencies(thr, Set(thr))
    },

    distributionCopyInternalDependencies <<= (state, distributionLibDirectory, projectDependenciesClosure) flatMap { (s, dld, pdc) =>
      val structure = Project.structure(s)
      val packageTaskKey = (packageBin in Compile).task
      val packageAllTask = pdc.flatMap(pr => (packageTaskKey in pr).get(structure.data)).toList.join

      packageAllTask.map { jars =>
        IO.copy(jars.map {f => (f, dld / f.getName)})
      }
    },

    distributionCopyBin <<= (distributionBinDirectory, resourceDirectory in Compile) map { (dbd, rd) =>
      val result = IO.copy((rd / "bin").listFiles().map(script => (script, dbd / script.getName)))
      result.filter(_.getName.endsWith(".sh")).foreach(file => file.setExecutable(true))
      result
    },

    distributionCopyConf <<= (distributionConfDirectory, resourceDirectory in Compile) map { (dcd, rd) =>
      IO.copy((rd / "conf").listFiles().map(script => (script, dcd / script.getName)))
    },

    distributionCopyDocs <<= (distributionDirectory, baseDirectory in Compile) map { (dd, bd) =>
      val docsFromRoot = List("README.md", "LICENSE.txt", "NOTICE.txt")
      IO.copy(docsFromRoot.map(d => (bd.getParentFile / d, dd / d)))
    },

    distributionClean <<= (distributionDirectory) map { (dd) => IO.delete(dd) },

    distribution <<= (streams, version, distributionDirectory,
      distributionCopyBin, distributionCopyConf, distributionCopyDocs,
      distributionCopyExternalDependencies, distributionCopyInternalDependencies) map { (s, v, dd, _, _, _, _, _) =>
      s.log.info("ElasticMQ distribution for version " + v.green + " created successfully in: " + dd.getPath)
    }
  )

  val generateVersionFileSettings = Seq(
    resourceGenerators in Compile <+= (version, resourceManaged in Compile) map { (v, t) =>
      val targetFile = t / "version"
      IO.write(targetFile, v.toString)
      Seq(targetFile)
    }
  )
}
