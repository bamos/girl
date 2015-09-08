name := "girl"

version := "1.0"

scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.6",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "io.spray" %% "spray-caching" % "1.3.2",
  "io.spray" %% "spray-can" % "1.3.2",
  "io.spray" %% "spray-routing" % "1.3.2",
  "org.jsoup" % "jsoup" % "1.8.3",
  "org.kohsuke" % "github-api" % "1.62",
  "org.eclipse.mylyn.github" % "org.eclipse.egit.github.core" % "2.1.5",
  "org.scalaz" %% "scalaz-core" % "7.1.1",
  "org.slf4j" % "slf4j-simple" % "1.7.10"
)

resolvers ++= Seq(
  "Maven Central" at "https://repo1.maven.org/maven2/",
  "Spray repo" at "http://repo.spray.io",
  "Jenkins repo" at "http://repo.jenkins-ci.org/releases/"
)

Revolver.settings

lazy val root = (project in file(".")).enablePlugins(SbtTwirl)

unmanagedResourceDirectories in Compile <<= Seq(
  baseDirectory / "src/main/webapp",
  baseDirectory / "src/main/resources"
).join
