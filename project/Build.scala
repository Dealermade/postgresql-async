import sbt._
import Keys._

object ProjectBuild extends Build {

  val commonName = "db-async-common"
  val postgresqlName = "postgresql-async"
  val mysqlName = "mysql-async"

  lazy val root = Project(
    id = "db-async-base",
    base = file("."),
    settings = Configuration.baseSettings ++ Seq(
      publish := (),
      publishLocal := (),
      publishArtifact := false
    ),
    aggregate = Seq(common, postgresql, mysql)
  )

  lazy val common = Project(
    id = commonName,
    base = file(commonName),
    settings = Configuration.baseSettings ++ Seq(
      name := commonName,
      libraryDependencies ++= Configuration.commonDependencies
    )
  )

  lazy val postgresql = Project(
    id = postgresqlName,
    base = file(postgresqlName),
    settings = Configuration.baseSettings ++ Seq(
      name := postgresqlName,
      libraryDependencies ++= Configuration.implementationDependencies
    )
  ) dependsOn (common)

  lazy val mysql = Project(
    id = mysqlName,
    base = file(mysqlName),
    settings = Configuration.baseSettings ++ Seq(
      name := mysqlName,
      libraryDependencies ++= Configuration.implementationDependencies
    )
  ) dependsOn (common)

}

object Configuration {

  val commonVersion = "0.2.22-IQ.5"
  val projectScalaVersion = "2.12.7"
  val specs2Version = "3.8.9"

  val specs2Dependency = "org.specs2" %% "specs2-core" % specs2Version % "test"
  val specs2JunitDependency = "org.specs2" %% "specs2-junit" % specs2Version % "test"
  val specs2MockDependency = "org.specs2" %% "specs2-mock" % specs2Version % "test"
  val logbackDependency = "ch.qos.logback" % "logback-classic" % "1.2.3" % "test"

  val commonDependencies = Seq(
    "org.slf4j" % "slf4j-api" % "1.7.25",
    "joda-time" % "joda-time" % "2.10",
    "org.joda" % "joda-convert" % "2.1.1",
    "io.netty" % "netty-all" % "4.1.29.Final",
    "org.javassist" % "javassist" % "3.23.1-GA",
    specs2Dependency,
    specs2JunitDependency,
    specs2MockDependency,
    logbackDependency
  )

  val implementationDependencies = Seq(
    specs2Dependency,
    logbackDependency
  )

  val baseSettings = Defaults.defaultSettings ++ Seq(
    scalaVersion := projectScalaVersion,
    scalacOptions :=
      Opts.compile.encoding("UTF8")
        :+ Opts.compile.deprecation
        :+ Opts.compile.unchecked
        :+ "-feature"
    ,
    testOptions in Test += Tests.Argument(TestFrameworks.Specs2, "sequential"),
    scalacOptions in doc := Seq("-doc-external-doc:scala=http://www.scala-lang.org/archives/downloads/distrib/files/nightly/docs/library/"),
    crossScalaVersions := Seq(projectScalaVersion, "2.10.6", "2.11.8", "2.12.7"),
    javacOptions := Seq("-source", "1.6", "-target", "1.6", "-encoding", "UTF8"),
    organization := "com.github.mauricio",
    version := commonVersion,
    parallelExecution := false,
    publishArtifact in Test := false,
    publishMavenStyle := true,
    pomIncludeRepository := {
      _ => false
    },
    credentials += {
      Seq("build.publish.host", "build.publish.user", "build.publish.password") map sys.props.get match {
        case Seq(Some(host), Some(user), Some(pass)) ⇒ Credentials("Sonatype Nexus Repository Manager", host, user, pass)
        case _                                       ⇒ Credentials(Path.userHome / ".ivy2" / ".credentials")
      }
    },
    pomExtra := (
      <url>https://github.com/mauricio/postgresql-async</url>
        <licenses>
          <license>
            <name>APACHE-2.0</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:mauricio/postgresql-netty.git</url>
          <connection>scm:git:git@github.com:mauricio/postgresql-netty.git</connection>
        </scm>
        <developers>
          <developer>
            <id>mauricio</id>
            <name>Maurício Linhares</name>
            <url>https://github.com/mauricio</url>
          </developer>
        </developers>
      )
  )

}
