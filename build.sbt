ThisBuild / scalaVersion := "3.3.8"
ThisBuild / organization := "locality"
ThisBuild / version := "0.1.0"
ThisBuild / javacOptions ++= Seq("--release", "21", "-encoding", "UTF-8")
ThisBuild / scalacOptions ++= Seq("-encoding", "UTF-8", "-release:21", "-deprecation", "-feature", "-unchecked")
lazy val llmCore = project.in(file("llm-core")).settings(
  name := "locality-llm-core",
  Test / fork := true,
  Test / test := (Test / runMain).toTask(" locality.llm.CoreTests").value
)
lazy val bench = project.in(file("bench")).dependsOn(llmCore).settings(
  name := "syntax-locality-icl",
  Compile / mainClass := Some("locality.bench.cli.Main"),
  Compile / run / fork := true,
  Compile / run / baseDirectory := (LocalRootProject / baseDirectory).value,
  Test / baseDirectory := (LocalRootProject / baseDirectory).value,
  Test / fork := true,
  Test / test := (Test / runMain).toTask(" locality.bench.BenchTests").value
)
lazy val root = project.in(file(".")).aggregate(llmCore, bench).settings(
  name := "syntax-locality-icl-root",
  publish / skip := true
)
