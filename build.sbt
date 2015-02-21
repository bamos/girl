name := "girl"

version := "1.0"

scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.6",
  "io.spray" %% "spray-caching" % "1.3.2",
  "io.spray" %% "spray-can" % "1.3.2",
  "io.spray" %% "spray-routing" % "1.3.2",
  "org.jsoup" % "jsoup" % "1.8.1",
  "org.kohsuke" % "github-api" % "1.62",
  "org.scalaz" %% "scalaz-core" % "7.1.1"
)

resolvers ++= Seq(
  "Maven Central" at "https://repo1.maven.org/maven2/",
  "Spray repo" at "http://repo.spray.io",
  "Jenkins repo" at "http://repo.jenkins-ci.org/releases/"
)

Revolver.settings
