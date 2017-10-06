import Dependencies._

val internalNexus = "http://nexuslab.alm/nexus/content/repositories"

lazy val projectSettings = Seq(
  organization := "com.jemberton",
  version := "0.1-SNAPSHOT",
  scalaVersion := "2.12.4",

  libraryDependencies ++= Seq(
    cats
  ),

  resolvers ++= Seq(
    "Typesafe Simple Repository" at "http://repo.typesafe.com/typesafe/simple/maven-releases/",
    "twitter-repo" at "https://maven.twttr.com"
  ),

  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-unchecked",
    "-Xlint"
  ),

  // This sends output in forked run to stdout by default (rather than stderr)
  outputStrategy in run := Some(StdoutOutput),

  mainClass in assembly := Some("com.expedia.cats.examples.Cats"),
  assemblyJarName in assembly := name.value,

  mainClass in run := Some("com.expedia.cats.examples.Cats"),

  assemblyMergeStrategy in assembly := {
    case PathList("org", "apache", xs@_*) => MergeStrategy.last
    case PathList("com", "google", xs@_*) => MergeStrategy.last
    case PathList(ps@_*) if ps.last endsWith ".html" => MergeStrategy.first
    case PathList(ps@_*) if ps.last endsWith ".types" => MergeStrategy.last
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
)

lazy val root =
  Project("cats-examples", file("."))
    .settings(projectSettings: _*)
