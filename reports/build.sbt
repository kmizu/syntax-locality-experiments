ThisBuild / scalaVersion := "3.3.8"
ThisBuild / organization := "locality"
ThisBuild / version := "0.1.0"
ThisBuild / javacOptions ++= Seq("--release", "21", "-encoding", "UTF-8")
ThisBuild / scalacOptions ++= Seq("-encoding", "UTF-8", "-release:21", "-deprecation", "-feature", "-unchecked")

lazy val root = project.in(file(".")).settings(
  name := "syntax-locality-html-report",
  Compile / mainClass := Some("publication.HtmlReport"),
  Compile / run / fork := true,
  Compile / run / baseDirectory := baseDirectory.value,
  Test / test := {
    val localData = baseDirectory.value / "data"
    val input = if (localData.isDirectory) localData else baseDirectory.value / ".." / "experiments" / "data"
    (Test / runner).value.run("publication.ExecutionSnapshotTests",
      (Test / fullClasspath).value.files, Vector(input.getCanonicalPath), streams.value.log).get
  },
  publish / skip := true
)
