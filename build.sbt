name := "girl"

version := "1.0"

scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
  "io.spray" %% "spray-can" % "1.3.2",
  "io.spray" %% "spray-routing" % "1.3.2",
  "com.typesafe.akka" %% "akka-actor" % "2.3.6"
)

resolvers ++= Seq(
  "Maven Central" at "https://repo1.maven.org/maven2/",
  "Spray repo" at "http://repo.spray.io"
)
