import sbt.Keys._

name := "Facebook"

def compile   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
def provided  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
def test      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
def runtime   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "runtime")
def container (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "container")

lazy val sprayV = "1.3.3"

lazy val akkaActor = "com.typesafe.akka" %% "akka-actor" % "2.3-SNAPSHOT"
lazy val akkaRemote = "com.typesafe.akka" %% "akka-remote" % "2.3-SNAPSHOT"
lazy val akkaSlf4j = "com.typesafe.akka" %%  "akka-slf4j" % "2.3-SNAPSHOT"
lazy val sprayCan = "io.spray" %%  "spray-can" % sprayV
lazy val sprayClient = "io.spray" %% "spray-client" % sprayV
lazy val sprayRouting = "io.spray" %% "spray-routing" % sprayV
lazy val sprayJson = "io.spray" %% "spray-json" % "1.3.2"
lazy val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.0.4"
lazy val mimepull = "org.jvnet.mimepull" % "mimepull" % "1.9.5"
lazy val logback = "ch.qos.logback" % "logback-classic" % "1.1.2"

lazy val commonSettings = Seq(
  version := "1.0",
  scalaVersion := "2.11.7",
  libraryDependencies += akkaActor,
  libraryDependencies += akkaRemote,
  libraryDependencies += scalaXml,
  libraryDependencies += sprayJson,
  resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/",
  resolvers += "Spray Repository" at "http://repo.spray.io/"
)

lazy val httpServer = Project("server", file("server")).
  settings(commonSettings: _*).
  settings(libraryDependencies ++=
    compile(mimepull, sprayCan, sprayRouting) ++
    runtime(logback)
  )

lazy val restServer = Project("restServer", file("restServer")).
  settings(commonSettings: _*).
  settings(libraryDependencies ++=
    compile(sprayCan, sprayRouting) ++
    runtime(logback)
  )

lazy val httpClient = Project("client", file("client")).
  dependsOn(httpServer).
  settings(commonSettings: _*).
  settings(libraryDependencies ++=
    compile(sprayClient) ++
    runtime(logback)
  )

lazy val encryptClient = Project("encryptClient", file("encryptClient")).
  dependsOn(httpServer).
  settings(commonSettings: _*).
  settings(libraryDependencies ++=
  compile(sprayClient) ++
    runtime(logback)
  )