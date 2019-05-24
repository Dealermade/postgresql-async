val commonName = "db-async-common"
val postgresqlName = "postgresql-async"

lazy val root = (project in file("."))
  .withId("db-async")
  .settings(
    baseSettings,
    publish := Unit,
    publishLocal := Unit,
    publishArtifact := false
  )
  .aggregate(common, postgresql)

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
  )
  .dependsOn(common)

val commonVersion = "0.3.8"
val specs2Version = "4.3.4"

val specs2Dependency = "org.specs2" %% "specs2-core" % specs2Version % "test"
val specs2JunitDependency = "org.specs2" %% "specs2-junit" % specs2Version % "test"
val specs2MockDependency = "org.specs2" %% "specs2-mock" % specs2Version % "test"
val logbackDependency = "ch.qos.logback" % "logback-classic" % "1.2.3" % "test"

val commonDependencies = Seq(
  "org.slf4j" % "slf4j-api" % "1.7.25",
  "joda-time" % "joda-time" % "2.10",
  "org.joda" % "joda-convert" % "2.1.1",
  "io.netty" % "netty-all" % "4.0.47.Final",
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

lazy val baseSettings = Seq(
  scalaVersion := "2.12.8",
  scalacOptions :=
    Opts.compile.encoding("UTF8")
      :+ Opts.compile.deprecation
      :+ Opts.compile.unchecked
      :+ "-feature"
      :+ "-language:postfixOps",
  Test / testOptions += Tests.Argument(TestFrameworks.Specs2, "sequential"),
  doc / scalacOptions := Seq(
    "-doc-external-doc:scala=http://www.scala-lang.org/archives/downloads/distrib/files/nightly/docs/library/"),
  javacOptions := Seq("-source", "1.6", "-target", "1.6", "-encoding", "UTF8"),
  version := commonVersion,
  parallelExecution := false,
  Test / publishArtifact := false,
  Test / fork := true,
  Test / baseDirectory := file("."),
  sonatypeProfileName := "com.github.dealermade",
  publishTo := Some(
    if (isSnapshot.value)
      Opts.resolver.sonatypeSnapshots
    else
      Opts.resolver.sonatypeStaging
  ),
  resolvers += "Sonatype OSS Release" at "https://oss.sonatype.org/content/repositories/releases",
  resolvers += "Sonatype Snapshot" at "https://oss.sonatype.org/content/repositories/snapshots",
  credentials += {
    Seq("SONATYPE_USER", "SONATYPE_PASS") map sys.env.get match {
      case Seq(Some(user), Some(pass)) ⇒
        Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)
      case _ ⇒
        Credentials(Path.userHome / ".sbt" / ".sonatype_credentials")
    }
  },
  useGpg := false,
  usePgpKeyHex("1037EE8F929780E1"),
  pgpPublicRing := baseDirectory.value / ".." / "project" / ".gnupg" / "pubring.gpg",
  pgpSecretRing := baseDirectory.value / ".." / "project" / ".gnupg" / "secring.gpg",
  pgpPassphrase := sys.env.get("PGP_PASS").map(_.toArray),
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
  ThisBuild / description := "Async, Netty based, database driver for PostgreSQL written in Scala",
  ThisBuild / licenses := List("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  ThisBuild / homepage := Some(url("https://github.com/example/project")),
  // Remove all additional repository other than Maven Central from POM
  ThisBuild / pomIncludeRepository := { _ =>
    false
  },
  ThisBuild / publishMavenStyle := true
)
