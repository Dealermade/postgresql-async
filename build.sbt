val commonName = "db-async-common"
val postgresqlName = "postgresql-async"
val mysqlName = "mysql-async"

lazy val root = (project in file("."))
  .withId("db-async")
  .settings(
    baseSettings,
    publish := Unit,
    publishLocal := Unit,
    publishArtifact := false
  )
  .aggregate(common, postgresql, mysql)

lazy val common = (project in file(commonName))
  .withId(commonName)
  .settings(
    baseSettings,
    name := commonName,
    libraryDependencies ++= commonDependencies
  )

lazy val postgresql = (project in file(postgresqlName))
  .withId(postgresqlName)
  .settings(
    baseSettings,
    name := postgresqlName,
    libraryDependencies ++= implementationDependencies
  ).dependsOn(common)

lazy val mysql = (project in file(mysqlName))
  .withId(mysqlName)
  .settings(
    baseSettings,
    name := mysqlName,
    libraryDependencies ++= implementationDependencies
  ).dependsOn(common)


val commonVersion = "0.3.0"
val specs2Version = "4.3.4"

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

val baseSettings = Seq(
  scalaVersion := "2.12.7",
  scalacOptions :=
    Opts.compile.encoding("UTF8")
      :+ Opts.compile.deprecation
      :+ Opts.compile.unchecked
      :+ "-feature"
      :+ "-language:postfixOps"
  ,
  Test / testOptions += Tests.Argument(TestFrameworks.Specs2, "sequential"),
  doc / scalacOptions := Seq("-doc-external-doc:scala=http://www.scala-lang.org/archives/downloads/distrib/files/nightly/docs/library/"),
  javacOptions := Seq("-source", "1.6", "-target", "1.6", "-encoding", "UTF8"),
  version := commonVersion,
  parallelExecution := false,
  Test / publishArtifact := false,
  Test / fork := true,
  Test / baseDirectory := file("."),
  publishTo := {
    version.value.endsWith("SNAPSHOT") match {
      case true => Some("snapshots" at "https://oss.sonatype.org/content/repositories/snapshots")
      case false => Some("releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2")
    }
  },
  resolvers += "Sonatype OSS Release" at "https://oss.sonatype.org/content/repositories/releases",
  resolvers += "Sonatype Snapshot" at "https://oss.sonatype.org/content/repositories/snapshots",
  credentials += {
    Seq("build.publish.host", "build.publish.user", "build.publish.password") map sys.props.get match {
      case Seq(Some(host), Some(user), Some(pass)) ⇒ Credentials("Sonatype Nexus Repository Manager", host, user, pass)
      case _ ⇒ Credentials(Path.userHome / ".sbt" / ".sonatype_credentials")
    }
  },
  useGpg := false,
  ThisBuild / organization := "com.github.dealermade",
  ThisBuild / organizationName := "Dealermade",
  ThisBuild / organizationHomepage := Some(url("https://github.com/Dealermade")),
  ThisBuild / scmInfo := Some(
    ScmInfo(
      url("https://github.com/Dealermade/postgresql-async"),
      "scm:git@github.com:Dealermade/postgresql-async.git"
    )
  ),
  ThisBuild / developers := List(
    Developer(
      id = "alexbezhan",
      name = "Alex Bezhan",
      email = "alex@dealermade.com",
      url = url("https://github.com/alexbezhan")
    )
  ),
  ThisBuild / description := "Async, Netty based, database drivers for MySQL and PostgreSQL written in Scala",
  ThisBuild / licenses := List("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  ThisBuild / homepage := Some(url("https://github.com/example/project")),
  // Remove all additional repository other than Maven Central from POM
  ThisBuild / pomIncludeRepository := { _ => false },
  ThisBuild / publishMavenStyle := true
)